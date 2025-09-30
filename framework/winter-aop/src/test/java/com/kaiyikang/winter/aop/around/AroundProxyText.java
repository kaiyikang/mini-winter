package com.kaiyikang.winter.aop.around;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.kaiyikang.winter.context.AnnotationConfigApplicationContext;
import com.kaiyikang.winter.io.PropertyResolver;

public class AroundProxyText {

    @Test
    public void testAroundProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(AroundApplication.class, createPropertyResolver())) {
            OriginBean proxy = ctx.getBean(OriginBean.class);
            System.out.println(proxy.getClass().getName());

            assertNotSame(OriginBean.class, proxy.getClass());
            assertNull(proxy.name);

            assertEquals("Hello, Bob!", proxy.hello());
            assertEquals("Morning, Bob.", proxy.morning());

            OtherBean other = ctx.getBean(OtherBean.class);
            assertSame(proxy, other.origin);
            assertEquals("Hello, Bob!", other.origin.hello());
        }
    }

    PropertyResolver createPropertyResolver() {
        var ps = new Properties();
        ps.put("customer.name", "Bob");
        var pr = new PropertyResolver(ps);
        return pr;
    }
}
