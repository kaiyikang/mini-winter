package com.kaiyikang.winter.aop.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.kaiyikang.winter.context.AnnotationConfigApplicationContext;
import com.kaiyikang.winter.io.PropertyResolver;

public class MetricProxyText {
    @Test
    public void testMetricProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(getClass(), createPropertyResolver())) {
            HashWorker worker = ctx.getBean(HashWorker.class);

            // should be proxy
            assertNotSame(HashWorker.class, worker.getClass());

            String md5 = "0x5d41402abc4b2a76b9719d911017c592";
            String sha1 = "0xaaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";
            String sha256 = "0x2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

            assertEquals(md5, worker.md5("hello"));
            assertEquals(sha1, worker.sha1("hello"));
            assertEquals(sha256, worker.sha256("hello"));

            MetricInvocationHandler metrics = ctx.getBean(MetricInvocationHandler.class);
            assertEquals(5, metrics.lastProcessedTime.get("MD5"));
            assertEquals(256, metrics.lastProcessedTime.get("SHA-256"));
            // cannot metric sha1() because it is a final method:
            assertNull(metrics.lastProcessedTime.get("SHA-1"));
        }
    }

    PropertyResolver createPropertyResolver() {
        Properties ps = new Properties();
        return new PropertyResolver(ps);
    }
}
