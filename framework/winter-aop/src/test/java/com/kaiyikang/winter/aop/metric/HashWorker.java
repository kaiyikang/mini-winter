package com.kaiyikang.winter.aop.metric;

import com.kaiyikang.winter.annotation.Component;

@Component
public class HashWorker extends BaseWorker {

    @Metric("SHA-1")
    public final String sha1(String input) {
        return hash("SHA-1", input);
    }

    @Metric("SHA-256")
    public final String sha256(String input) {
        return hash("SHA-256", input);
    }
}
