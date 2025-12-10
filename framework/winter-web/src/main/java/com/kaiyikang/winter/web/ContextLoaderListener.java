package com.kaiyikang.winter.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.winter.context.AnnotationConfigApplicationContext;
import com.kaiyikang.winter.context.ApplicationContext;
import com.kaiyikang.winter.exception.NestedRuntimeException;
import com.kaiyikang.winter.io.PropertyResolver;
import com.kaiyikang.winter.web.utils.WebUtils;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

public class ContextLoaderListener implements ServletContextListener {

    final Logger logger = LoggerFactory.getLogger(getClass());

    /* web context的入口，加载配置，设置全局编码，创建dispatcherServlet，并且挂载WinterSpring到context */
    @Override
    public void contextInitialized(ServletContextEvent event) {
        logger.info("init {}.", getClass().getName());
        var servletContext = event.getServletContext();
        var propertyResolver = WebUtils.createPropertyResolver();
        WebMvcConfiguration.setServletContext(servletContext);

        // Setup encoding
        String encoding = propertyResolver.getProperty("${winter.web.character-encoding:UTF-8}");
        servletContext.setRequestCharacterEncoding(encoding);
        servletContext.setResponseCharacterEncoding(encoding);

        // Initial applicationContext
        var applicationContext = createApplicationContext(servletContext.getInitParameter("configuration"),
                propertyResolver);

        WebUtils.registerDispatcherServlet(servletContext, propertyResolver);
        servletContext.setAttribute("applicationContext", applicationContext);
    }

    ApplicationContext createApplicationContext(String configClassName, PropertyResolver propertyResolver) {
        logger.info("init ApplicationContext by configuration: {}", configClassName);

        if (configClassName == null || configClassName.isEmpty()) {
            throw new NestedRuntimeException(
                    "Cannot init ApplicationContext for missing init param name: configuration");
        }

        Class<?> configClass;
        try {
            configClass = Class.forName(configClassName);
        } catch (ClassNotFoundException e) {
            throw new NestedRuntimeException(
                    "Could not load class from init param 'configuration': " + configClassName);
        }
        return new AnnotationConfigApplicationContext(configClass, propertyResolver);
    }
}
