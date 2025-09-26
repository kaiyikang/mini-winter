package com.kaiyikang.winter.aop;

public class OriginBean {

    public String name;

    /**
     * Pointcut
     */
    @Polite
    public String hello() {
        return "Hello, " + name + ".";
    }

    public String morning() {
        return "Morning, " + name + ".";
    }
}
