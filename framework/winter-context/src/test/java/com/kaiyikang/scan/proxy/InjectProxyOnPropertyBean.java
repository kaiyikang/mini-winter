package com.kaiyikang.scan.proxy;

import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.Component;

@Component
public class InjectProxyOnPropertyBean {

    @Autowired
    public OriginBean injected;
}
