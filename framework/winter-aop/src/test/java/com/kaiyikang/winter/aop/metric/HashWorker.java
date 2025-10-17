package com.kaiyikang.winter.aop.metric;

import com.kaiyikang.winter.annotation.Component;

@Component
public class HashWorker extends BaseWorker {

    /**
     * AOP frameworks intercept method calls through subclass proxies (CGLIB).
     * 
     * The principle of subclass proxies is: They create a subclass of the target
     * class and override the methods that need to be enhanced within this subclass.
     * 
     * However, final methods in Java cannot be overridden by subclasses.
     * 
     * Therefore, when a class contains final methods, the proxy framework cannot
     * create a proxy class and will directly return the original class.
     */
    @Metric("SHA-1")
    public final String sha1(String input) {
        return hash("SHA-1", input);
    }

    @Metric("SHA-256")
    public String sha256(String input) {
        return hash("SHA-256", input);
    }
}
