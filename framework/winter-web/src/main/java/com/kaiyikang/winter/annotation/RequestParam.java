package com.kaiyikang.winter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.kaiyikang.winter.web.utils.WebUtils;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {
    // ?page=1&size=20
    String value();

    String defaultValue() default WebUtils.DEFAULT_PARAM_VALUE;
}
