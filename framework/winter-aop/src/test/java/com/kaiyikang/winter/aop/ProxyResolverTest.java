package com.kaiyikang.winter.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class ProxyResolverTest {

    @Test
    public void testProxyResolver() {
        // Given
        OriginBean origin = new OriginBean();
        origin.name = "Bob";
        // original string
        assertEquals("Hello, Bob.", origin.hello());

        // When
        // Create proxy
        OriginBean proxy = new ProxyResolver().createProxy(origin, new PoliteInvocationHandler());
        System.out.println(proxy.getClass().getName());

        // Then
        assertNotSame(OriginBean.class, proxy.getClass());
        assertNull(proxy.name);
        assertEquals("Hello, Bob!", proxy.hello(), "Test with @Polite");
        assertEquals("Morning, Bob.", proxy.morning(), "Test without @Polite");

    }

}
