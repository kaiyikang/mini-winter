package com.kaiyikang.winter.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.winter.annotation.Controller;
import com.kaiyikang.winter.annotation.GetMapping;
import com.kaiyikang.winter.annotation.PostMapping;
import com.kaiyikang.winter.annotation.ResponseBody;
import com.kaiyikang.winter.annotation.RestController;
import com.kaiyikang.winter.context.ApplicationContext;
import com.kaiyikang.winter.context.ConfigurableApplicationContext;
import com.kaiyikang.winter.exception.ErrorResponseException;
import com.kaiyikang.winter.exception.NestedRuntimeException;
import com.kaiyikang.winter.exception.ServerErrorException;
import com.kaiyikang.winter.exception.ServerWebInputException;
import com.kaiyikang.winter.io.PropertyResolver;
import com.kaiyikang.winter.web.utils.JsonUtils;
import com.kaiyikang.winter.web.utils.PathUtils;
import com.kaiyikang.winter.web.utils.WebUtils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

    void doService(String url, HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers) {
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
                handleMvcResult(url, resp, dispatcher, r);
            }
            // already handled
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

        }

        // ModelAndView
        if (r instanceof ModelAndView mv) {

            return;
        }

        // other cases
        if (!dispatcher.isVoid && r != null) {
            throw new ServletException(
                    "Unable to process " + r.getClass().getName() + " result when handle url: " + url);
        }
    }

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

        public Param(String httpMethod, Method method, Parameter parameter, Annotation[] annotations)
                throws ServletException {

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
