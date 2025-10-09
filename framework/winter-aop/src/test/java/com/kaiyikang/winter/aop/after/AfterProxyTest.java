package com.kaiyikang.winter.aop.after;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.kaiyikang.winter.context.AnnotationConfigApplicationContext;
import com.kaiyikang.winter.io.PropertyResolver;

public class AfterProxyTest {

    @Test
    public void testAfterProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(AfterApplication.class, createPropertyResolver())) {
            GreetingBean proxy = ctx.getBean(GreetingBean.class);

            assertEquals("Hello, Bob!", proxy.hello("Bob"));
            assertEquals("Morning, Alice!", proxy.morning("Alice"));
        }
    }

    PropertyResolver createPropertyResolver() {
        var ps = new Properties();
        var pr = new PropertyResolver(ps);
        return pr;
    }
}
