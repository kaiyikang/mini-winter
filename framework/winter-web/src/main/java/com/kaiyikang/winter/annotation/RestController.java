package com.kaiyikang.winter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface RestController {
    // @Controller + @ResponseBody的组合注释
    // 类里的所有返回值，都是作为JSON写会到HTTP相应中
    String value() default "";
}
