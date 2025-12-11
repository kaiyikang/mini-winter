package com.kaiyikang.winter.web.utils;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.winter.context.ApplicationContextUtils;
import com.kaiyikang.winter.io.PropertyResolver;
import com.kaiyikang.winter.utils.ClassPathUtils;
import com.kaiyikang.winter.utils.YamlUtils;
import com.kaiyikang.winter.web.DispatcherServlet;
import com.kaiyikang.winter.web.FilterRegistrationBean;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;

public class WebUtils {

    public static final String DEFAULT_PARAM_VALUE = "\0\t\0\t\0";

    static final Logger logger = LoggerFactory.getLogger(WebUtils.class);

    static final String CONFIG_APP_YAML = "/application.yml";
    static final String CONFIG_APP_PROP = "/application.properties";

    /*
     * 绑定一个叫做dispatcherServlet的绑定到root路径
     */
    public static void registerDispatcherServlet(ServletContext servletContext, PropertyResolver propertyResolver) {
        var dispatcherServlet = new DispatcherServlet(ApplicationContextUtils.getRequiredApplicationContext(),
                propertyResolver);
        logger.info("register servlet {} for URL '/'", dispatcherServlet.getClass().getName());

        var dispatcherReg = servletContext.addServlet("dispatcherServlet", dispatcherServlet);

        if (dispatcherReg == null) {
            return;
        }
        dispatcherReg.addMapping("/");
        dispatcherReg.setLoadOnStartup(0);
    }

    public static void registerFilters(ServletContext servletContext) {
        var applicationContext = ApplicationContextUtils.getApplicationContext();
        // Register all Filter which implements FilterRegistrationBean
        for (var filterRegBean : applicationContext.getBeans(FilterRegistrationBean.class)) {
            List<String> urlPatterns = filterRegBean.getUrlPatterns();

            if (urlPatterns == null || urlPatterns.isEmpty()) {
                throw new IllegalArgumentException("No url patterns for {}" + filterRegBean.getClass().getName());
            }

            var filter = Objects.requireNonNull(filterRegBean.getFilter(),
                    "FilterRegistrationBean.getFilter() must not return null.");
            logger.info("register filter '{}' {} for URLs: {}",
                    filterRegBean.getName(),
                    filter.getClass().getName(),
                    String.join(", ", urlPatterns));

            var filterReg = servletContext.addFilter(filterRegBean.getName(), filter);
            filterReg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true,
                    urlPatterns.toArray(String[]::new));
        }
    }

    public static PropertyResolver createPropertyResolver() {
        final Properties properties = new Properties();
        try {
            Map<String, Object> yamlMap = YamlUtils.loadYamlAsPlainMap(CONFIG_APP_YAML);
            logger.info("load config: {}", CONFIG_APP_YAML);
            for (String key : yamlMap.keySet()) {
                Object value = yamlMap.get(key);
                if (value instanceof String strValue) {
                    properties.put(key, strValue);
                }
            }
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                ClassPathUtils.readInputStream(CONFIG_APP_PROP, (input) -> {
                    logger.info("load config: {}", CONFIG_APP_PROP);
                    properties.load(input);
                    return true;
                });
            }
        }
        return new PropertyResolver(properties);
    }
}
