package com.kaiyikang.winter.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

public class ProxyResolverTest {

    @Test
    public void testProxyResolver() {
        OriginBean origin = new OriginBean();

        origin.name = "Bob";

        assertEquals("Hello, Bob.", origin.hello());

        // Create proxy
        OriginBean proxy = new ProxyResolver().createProxy(origin, new PoliteInvocationHandler());

        System.out.println(proxy.getClass().getName());

        assertNotSame(OriginBean.class, proxy.getClass());

        assertNull(proxy.name);

        assertEquals("Hello, Bob!", proxy.hello(), "Test with @Polite");
        assertEquals("Morning, Bob!", proxy.hello(), "Test without @Polite");

    }

}
