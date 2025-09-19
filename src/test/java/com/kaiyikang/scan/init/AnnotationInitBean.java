package com.kaiyikang.scan.init;

import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.annotation.Value;

import jakarta.annotation.PostConstruct;

@Component
public class AnnotationInitBean {
    @Value("${app.title}")
    String appTitle;

    @Value("${app.version}")
    String appVersion;

    public String appName;

    @PostConstruct
    void init() {
        this.appName = this.appTitle + " / " + this.appVersion;
    }
}
