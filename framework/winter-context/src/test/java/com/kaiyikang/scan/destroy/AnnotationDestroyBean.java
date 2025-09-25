package com.kaiyikang.scan.destroy;

import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.annotation.Value;

import jakarta.annotation.PreDestroy;

@Component
public class AnnotationDestroyBean {

    @Value("${app.title}")
    public String appTitle;

    @PreDestroy
    void destroy() {
        this.appTitle = null;
    }
}
