package com.kaiyikang.winter.web;

import java.util.Objects;

import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Value;

import jakarta.servlet.ServletContext;

@Configuration
public class WebMvcConfiguration {

    private static ServletContext servletContext = null;

    public static void setServletContext(ServletContext ctx) {
        servletContext = ctx;
    }

    @Bean(initMethod = "init")
    ViewResolver viewResolver(
            @Autowired ServletContext servletContext,
            @Value("${winter.web.freemarker.template-path:/WEB-INF/templates}") String templatePath,
            @Value("${winter.web.freemarker.template-encoding:UTF-8}") String templateEncoding) {
        return new FreeMarkerViewResolver(servletContext, templatePath, templateEncoding);
    }

    @Bean
    ServletContext servletContext() {
        return Objects.requireNonNull(servletContext, "ServletContext is not set.");
    }
}
