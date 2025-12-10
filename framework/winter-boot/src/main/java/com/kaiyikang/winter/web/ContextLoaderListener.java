package com.kaiyikang.winter.web;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.winter.context.AnnotationConfigApplicationContext;
import com.kaiyikang.winter.context.ApplicationContext;
import com.kaiyikang.winter.io.PropertyResolver;
import com.kaiyikang.winter.web.utils.WebUtils;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

public class ContextLoaderListener implements ServletContainerInitializer {

    final Logger logger = LoggerFactory.getLogger(getClass());
    final Class<?> configClass;
    final PropertyResolver propertyResolver;

    public ContextLoaderListener(Class<?> configClass, PropertyResolver propertyResolver) {
        this.configClass = configClass;
        this.propertyResolver = propertyResolver;
    }

    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
        logger.info("Servlet container start. ServletContext = {}", ctx);

        String encoding = propertyResolver.getProperty("${winter.web.character-encoding:UTF-8}");
        ctx.setRequestCharacterEncoding(encoding);
        ctx.setResponseCharacterEncoding(encoding);

        WebMvcConfiguration.setServletContext(ctx);
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(this.configClass,
                this.propertyResolver);
        logger.info("Appplication context created: {}", applicationContext);

        WebUtils.registerFilters(ctx);
        WebUtils.registerDispatcherServlet(ctx, this.propertyResolver);
    }
}
