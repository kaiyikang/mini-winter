package com.kaiyikang.scan.proxy;

import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.Component;

@Component
public class InjectProxyOnConstructorBean {
    public OriginBean injected;

    public InjectProxyOnConstructorBean(@Autowired OriginBean injected) {
        this.injected = injected;
    }
}
