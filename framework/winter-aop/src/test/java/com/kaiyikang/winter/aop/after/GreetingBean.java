package com.kaiyikang.winter.aop.after;

import com.kaiyikang.winter.annotation.Around;
import com.kaiyikang.winter.annotation.Component;

@Component
@Around("politeInvocationHandler")
public class GreetingBean {

    public String hello(String name) {
        return "Hello, " + name + ".";
    }

    public String morning(String name) {
        return "Morning, " + name + ".";
    }
}
