package com.kaiyikang.winter.aop.around;

import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.annotation.Order;

@Order(0)
@Component
public class OtherBean {

    public OriginBean origin;

    public OtherBean(@Autowired OriginBean origin) {
        this.origin = origin;
    }
}
