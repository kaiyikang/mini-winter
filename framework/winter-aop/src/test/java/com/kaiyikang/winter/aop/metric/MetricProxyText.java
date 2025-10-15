package com.kaiyikang.winter.aop.metric;

import static org.junit.jupiter.api.Assertions.assertNotSame;

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

            // TODO
        }
    }

    PropertyResolver createPropertyResolver() {
        Properties ps = new Properties();
        return new PropertyResolver(ps);
    }
}
