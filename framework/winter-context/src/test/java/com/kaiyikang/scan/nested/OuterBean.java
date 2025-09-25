package com.kaiyikang.scan.nested;

import com.kaiyikang.winter.annotation.Component;

@Component
public class OuterBean {
    @Component
    public static class NestedBean {

    }
}
