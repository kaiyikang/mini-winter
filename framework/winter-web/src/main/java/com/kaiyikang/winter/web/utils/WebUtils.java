package com.kaiyikang.winter.web.utils;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.winter.context.ApplicationContextUtils;
import com.kaiyikang.winter.io.PropertyResolver;
import com.kaiyikang.winter.utils.ClassPathUtils;
import com.kaiyikang.winter.utils.YamlUtils;
import com.kaiyikang.winter.web.DispatcherServlet;

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
        dispatcherReg.addMapping("/");
        dispatcherReg.setLoadOnStartup(0);
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
