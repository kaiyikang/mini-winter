package com.kaiyikang.winter.web;

import java.util.List;

import jakarta.servlet.Filter;

// 注册自定义Filter。
// 框架会扫描它的子类，调用getFilter，得到实例，getUrlPatterns获得拦截URL，最后注册它
public abstract class FilterRegistrationBean {

    public abstract List<String> getUrlPatterns();

    public String getName() {
        String name = getClass().getSimpleName();
        name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        if (name.endsWith("FilterRegistrationBean") && name.length() > "FilterRegistrationBean".length()) {
            return name.substring(0, name.length() - "FilterRegistrationBean".length());
        }
        if (name.endsWith("FilterRegistration") && name.length() > "FilterRegistration".length()) {
            return name.substring(0, name.length() - "FilterRegistration".length());
        }
        return name;
    }

    public abstract Filter getFilter();
}
