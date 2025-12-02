package com.kaiyikang.winter.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.util.ClassUtil;
import com.kaiyikang.winter.annotation.Controller;
import com.kaiyikang.winter.annotation.GetMapping;
import com.kaiyikang.winter.annotation.PathVariable;
import com.kaiyikang.winter.annotation.PostMapping;
import com.kaiyikang.winter.annotation.RequestBody;
import com.kaiyikang.winter.annotation.RequestParam;
import com.kaiyikang.winter.annotation.ResponseBody;
import com.kaiyikang.winter.annotation.RestController;
import com.kaiyikang.winter.context.ApplicationContext;
import com.kaiyikang.winter.context.ConfigurableApplicationContext;
import com.kaiyikang.winter.exception.ErrorResponseException;
import com.kaiyikang.winter.exception.NestedRuntimeException;
import com.kaiyikang.winter.exception.ServerErrorException;
import com.kaiyikang.winter.exception.ServerWebInputException;
import com.kaiyikang.winter.io.PropertyResolver;
import com.kaiyikang.winter.utils.ClassUtils;
import com.kaiyikang.winter.web.utils.JsonUtils;
import com.kaiyikang.winter.web.utils.PathUtils;
import com.kaiyikang.winter.web.utils.WebUtils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class DispatcherServlet extends HttpServlet {

    final Logger logger = LoggerFactory.getLogger(getClass());

    ApplicationContext applicationContext;
    ViewResolver viewResolver;

    String resourcePath;
    String faviconPath;

    List<Dispatcher> getDispatchers = new ArrayList<>();
    List<Dispatcher> postDispatchers = new ArrayList<>();

    public DispatcherServlet(ApplicationContext applicationContext, PropertyResolver propertyResolver) {
        this.applicationContext = applicationContext;
        this.viewResolver = applicationContext.getBean(ViewResolver.class);
        this.resourcePath = propertyResolver.getProperty("${winter.web.static-path:/static/}");
        this.faviconPath = propertyResolver.getProperty("${winter.web.favicon-path:/favicon.ico}");
        if (!this.resourcePath.endsWith("/")) {
            this.resourcePath = this.resourcePath + "/";
        }
    }

    @Override
    public void init() throws ServletException {
        logger.info("init {}.", getClass().getName());
        // 从applicationContext中获取所有的beanDef
        for (var def : ((ConfigurableApplicationContext) this.applicationContext).findBeanDefinitions(Object.class)) {
            Class<?> beanClass = def.getBeanClass();
            Object beanInstance = def.getRequiredInstance();
            // 获取Annotation
            Controller controller = beanClass.getAnnotation(Controller.class);
            RestController restController = beanClass.getAnnotation(RestController.class);
            if (controller != null && restController != null) {
                throw new ServletException("Found @Controller and @RestController on class: " + beanClass.getName());
            }
            if (controller != null) {
                addController(false, def.getName(), beanInstance);
            }
            if (restController != null) {
                addController(true, def.getName(), beanInstance);
            }
        }
    }

    @Override
    public void destroy() {
        this.applicationContext.close();
    }

    void addController(boolean isRest, String name, Object instance) throws ServletException {
        logger.info("add {} controller '{}' : {}", isRest ? "REST" : "MVC", name, instance.getClass().getName());
        addMethods(isRest, name, instance, instance.getClass());
    }

    void addMethods(boolean isRest, String name, Object instance, Class<?> type) throws ServletException {
        for (Method m : type.getDeclaredMethods()) {
            GetMapping get = m.getAnnotation(GetMapping.class);
            if (get != null) {
                checkMethod(m);
                // value 是@GetMapping中的路径
                this.getDispatchers.add(new Dispatcher("GET", isRest, instance, m, get.value()));
            }
            PostMapping post = m.getAnnotation(PostMapping.class);
            if (post != null) {
                checkMethod(m);
                this.postDispatchers.add(new Dispatcher("POST", isRest, instance, m, post.value()));
            }
        }
        // 加入父类的方法
        Class<?> superClass = type.getSuperclass();
        if (superClass != null) {
            addMethods(isRest, name, instance, superClass);
        }
    }

    void checkMethod(Method m) throws ServletException {
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) {
            throw new ServletException("Cannot do URL mapping to a static method: " + m);
        }
        m.setAccessible(true);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String url = req.getRequestURI();
        if (url.equals(this.faviconPath) || url.startsWith(this.resourcePath)) {
            doResource(url, req, resp);
        } else {
            doService(req, resp, getDispatchers);
        }

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doService(req, resp, postDispatchers);
    }

    /* 捕获所有的可能，并且转换成合适的HTTP错误响应 */
    void doService(HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers)
            throws ServletException, IOException {
        String url = req.getRequestURI();
        try {
            doService(url, req, resp, dispatchers);
        } catch (ErrorResponseException e) {
            logger.warn("process request failed with status " + e.statusCode + " : " + url, e);
            if (!resp.isCommitted()) {
                resp.resetBuffer();
                resp.sendError(e.statusCode);
            }
        } catch (RuntimeException | ServletException | IOException e) {
            logger.warn("process request failed: " + url, e);
            throw e;
        } catch (Exception e) {
            logger.warn("process request failed: " + url, e);
            throw new NestedRuntimeException();
        }
    }

    // === Begin of doService ===
    void doService(String url, HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers)
            throws Exception {
        for (Dispatcher dispatcher : dispatchers) {
            Result result = dispatcher.process(url, req, resp);
            // find the first true
            if (!result.processed()) {
                continue;
            }
            Object r = result.returnObject();
            if (dispatcher.isRest) {
                handleRestResult(url, resp, dispatcher, r);
            } else {
                handleMvcResult(url, req, resp, dispatcher, r);
            }
            // exist one dispatcher to handle, return it directly
            return;
        }

        resp.sendError(404, "Not Found");
    }

    private void handleRestResult(String url,
            HttpServletResponse resp,
            Dispatcher dispatcher,
            Object r) throws Exception {
        // r 是方法执行完的返回值
        if (!resp.isCommitted()) {
            resp.setContentType("application/json");
        }
        // 如果有 @ResponseBody
        if (dispatcher.isResponseBody) {
            // 发送 string
            if (r instanceof String s) {
                PrintWriter pw = resp.getWriter();
                pw.write(s);
                pw.flush();
            }
            // 发送字节数据
            if (r instanceof byte[] data) {
                ServletOutputStream output = resp.getOutputStream();
                output.write(data);
                output.flush();
                return;
            }

            throw new ServletException("Unable to process REST result when handle url: " + url);
        }
        // 不是 ResponseBody的方法，对象序列化成 Json
        if (!dispatcher.isVoid) {
            PrintWriter pw = resp.getWriter();
            JsonUtils.writeJson(pw, r);
            pw.flush();
        }
    }

    private void handleMvcResult(String url,
            HttpServletRequest req,
            HttpServletResponse resp,
            Dispatcher dispatcher,
            Object r) throws Exception {
        if (!resp.isCommitted()) {
            resp.setContentType("text/html");
        }

        if (r instanceof String s) {
            if (dispatcher.isResponseBody) {
                PrintWriter pw = resp.getWriter();
                pw.write(s);
                pw.flush();
                return;
            }

            if (s.startsWith("redirect:")) {
                resp.sendRedirect(s.substring(9));
                return;
            }

            throw new ServletException("Unable to process String result when handle url: " + url);
        }

        // byte[]
        if (r instanceof byte[] data) {
            if (dispatcher.isResponseBody) {
                ServletOutputStream output = resp.getOutputStream();
                output.write(data);
                output.flush();
                return;
            }
            throw new ServletException("Unable to process byte[] result when handle url: " + url);

        }

        // ModelAndView
        if (r instanceof ModelAndView mv) {
            String view = mv.getViewName();
            if (view.startsWith("redirect:")) {
                resp.sendRedirect(view.substring(9));
            } else {
                this.viewResolver.render(view, mv.getModel(), req, resp);
            }
            return;
        }

        // other cases
        if (!dispatcher.isVoid && r != null) {
            throw new ServletException(
                    "Unable to process " + r.getClass().getName() + " result when handle url: " + url);
        }
    }

    // === End of doService ===

    /* 寻找/webapps/myapp/static/*的资源，并返回给浏览器 */
    void doResource(String url, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ServletContext ctx = req.getServletContext();
        try (InputStream input = ctx.getResourceAsStream(url)) {
            if (input == null) {
                resp.sendError(404, "Not Found");
                return;
            }
            // 获取文件名判断mime类型
            String file = url;
            int n = url.lastIndexOf('/');
            if (n >= 0) {
                file = url.substring(n + 1);
            }
            String mime = ctx.getMimeType(file);
            if (mime == null) {
                mime = "application/octect-stream";
            }
            resp.setContentType(mime);
            ServletOutputStream output = resp.getOutputStream();
            input.transferTo(output);
            output.flush();
        }
    }

    static class Dispatcher {

        final static Result NOT_PROCESSED = new Result(false, null);
        final Logger logger = LoggerFactory.getLogger(getClass());

        boolean isRest;
        boolean isResponseBody;
        boolean isVoid;
        Pattern urlPattern;
        Object controller;
        Method handlerMethod;
        Param[] methodParameters;

        public Dispatcher(String httpMethod, boolean isRest, Object controller, Method method, String urlPattern)
                throws ServletException {
            this.isRest = isRest;
            this.isResponseBody = method.getAnnotation(ResponseBody.class) != null;
            this.isVoid = method.getReturnType() == void.class;
            this.urlPattern = PathUtils.compile(urlPattern);
            this.controller = controller;
            this.handlerMethod = method;
            Parameter[] params = method.getParameters();
            Annotation[][] paramsAnnos = method.getParameterAnnotations();
            this.methodParameters = new Param[params.length];
            for (int i = 0; i < params.length; i++) {
                this.methodParameters[i] = new Param(httpMethod, method, params[i], paramsAnnos[i]);
            }

            logger.atDebug().log("mapping {} to handler {}.{}", urlPattern, controller.getClass().getSimpleName(),
                    method.getName());
            if (logger.isDebugEnabled()) {
                for (var p : this.methodParameters) {
                    logger.debug("> parameter: {}", p);
                }
            }
        }

        Result process(String url, HttpServletRequest request, HttpServletResponse response) throws Exception {
            // Dispatcher 会保存编译后的 URL 正则表达式
            // 匹配时会用这个正则匹配当前请求的原始 URL
            Matcher matcher = urlPattern.matcher(url);

            // 不匹配，即不是当前的URL处理者，请尝试下一个
            if (!matcher.matches()) {
                return NOT_PROCESSED;
            }

            // 匹配，开始处理
            Object[] arguments = new Object[this.methodParameters.length];
            // 提取 controller 的实际参数，并转化为目标类型
            for (int i = 0; i < arguments.length; i++) {
                Param param = methodParameters[i];
                arguments[i] = switch (param.paramType) {
                    case PATH_VARIABLE -> {
                        try {
                            String s = matcher.group(param.name);
                            yield convertToType(param.classType, s);
                        } catch (IllegalArgumentException e) {
                            throw new ServerWebInputException("Path variable '" + param.name + "' not found.");
                        }
                    }
                    case REQUEST_BODY -> {
                        BufferedReader reader = request.getReader();
                        yield JsonUtils.readJson(reader, param.classType);
                    }
                    case REQUEST_PARAM -> {
                        String s = getOrDefault(request, param.name, param.defaultValue);
                        yield convertToType(param.classType, s);
                    }
                    case SERVLET_VARIABLE -> {
                        Class<?> classType = param.classType;
                        if (classType == HttpServletRequest.class) {
                            yield request;
                        } else if (classType == HttpServletResponse.class) {
                            yield response;
                        } else if (classType == HttpSession.class) {
                            yield request.getSession();
                        } else if (classType == ServletContext.class) {
                            yield request.getServletContext();
                        } else {
                            throw new ServerErrorException("Could not determine argument type: " + classType);

                        }
                    }
                };
            }
            // 触发controller，并获得结果
            Object result = null;
            try {
                result = this.handlerMethod.invoke(this.controller, arguments);
            } catch (InvocationTargetException e) {
                // 异常解包（unwrap）
                Throwable t = e.getCause();
                if (t instanceof Exception ex) {
                    throw ex;
                }
                // JVM 的错误
                throw e;
            } catch (ReflectiveOperationException e) {
                throw new ServerErrorException(e);
            }
            // 封装最后的结果
            return new Result(true, result);
        }

        Object convertToType(Class<?> classType, String s) {
            if (classType == String.class) {
                return s;
            } else if (classType == boolean.class || classType == Boolean.class) {
                return Boolean.valueOf(s);
            } else if (classType == int.class || classType == Integer.class) {
                return Integer.valueOf(s);
            } else if (classType == long.class || classType == Long.class) {
                return Long.valueOf(s);
            } else if (classType == byte.class || classType == Byte.class) {
                return Byte.valueOf(s);
            } else if (classType == short.class || classType == Short.class) {
                return Short.valueOf(s);
            } else if (classType == float.class || classType == Float.class) {
                return Float.valueOf(s);
            } else if (classType == double.class || classType == Double.class) {
                return Double.valueOf(s);
            } else {
                throw new ServerErrorException("Could not determine argument type: " + classType);
            }
        }

        /*
         * 为了处理url中的parameter，
         * e.g. /api/user?id=1&verbose=true
         */
        String getOrDefault(HttpServletRequest request, String name, String defaultValue) {
            String s = request.getParameter(name);
            if (s == null) {
                if (WebUtils.DEFAULT_PARAM_VALUE.equals(defaultValue)) {
                    throw new ServerWebInputException("Request parameter '" + name + "' not found.");
                }
                return defaultValue;
            }
            return s;
        }
    }

    static enum ParamType {
        PATH_VARIABLE, REQUEST_PARAM, REQUEST_BODY, SERVLET_VARIABLE;
    }

    static class Param {

        String name;
        ParamType paramType;
        Class<?> classType;
        String defaultValue;

        /*
         * 这是修饰method中的param的，它可以来自url，来自query或是body
         */
        public Param(String httpMethod, Method method, Parameter parameter, Annotation[] annotations)
                throws ServletException {
            PathVariable pv = ClassUtils.getAnnotation(annotations, PathVariable.class);
            RequestParam rp = ClassUtils.getAnnotation(annotations, RequestParam.class);
            RequestBody rb = ClassUtils.getAnnotation(annotations, RequestBody.class);

            int total = (pv == null ? 0 : 1) + (rp == null ? 0 : 1) + (rb == null ? 0 : 1);
            if (total > 1) {
                throw new ServletException(
                        "Annotation @PathVariable, @RequestParam and @RequestBody cannot be combined at method: "
                                + method);
            }
            this.classType = parameter.getType();
            if (pv != null) {
                this.name = pv.value();
                this.paramType = ParamType.PATH_VARIABLE;
            } else if (rp != null) {
                this.name = rp.value();
                this.defaultValue = rp.defaultValue();
                this.paramType = ParamType.REQUEST_PARAM;
            } else if (rb != null) {
                this.paramType = ParamType.REQUEST_BODY;
            } else {
                // test(HttpServletRequest req, HttpServletResponse resp)
                this.paramType = ParamType.SERVLET_VARIABLE;
                if (this.classType != HttpServletRequest.class
                        && this.classType != HttpServletResponse.class
                        && this.classType != HttpSession.class
                        && this.classType != ServletContext.class) {
                    throw new ServerErrorException(
                            "(Missing annotation?) Unsupported argument type: " + classType + " at method: " + method);
                }
            }
        }

        @Override
        public String toString() {
            return "Param [name=" + name + ", paramType=" + paramType + ", classType=" + classType + ", defaultValue="
                    + defaultValue + "]";
        }
    }

    // 这里的static确保它不会持有 DispatcherServlet.this
    static record Result(boolean processed, Object returnObject) {
    }
}
