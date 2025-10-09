package com.kaiyikang.winter.aop.around;

import com.kaiyikang.winter.annotation.Around;
import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.annotation.Value;

@Component
@Around("aroundInvocationHandler")
public class OriginBean {

    @Value("${customer.name}")
    public String name;

    @Polite
    public String hello() {
        return "Hello, " + name + ".";
    }

    public String morning() {
        return "Morning, " + name + ".";
    }
}
