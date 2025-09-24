package com.kaiyikang.winter.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kaiyikang.imported.LocalDateConfiguration;
import com.kaiyikang.imported.ZonedDateConfiguration;
import com.kaiyikang.scan.ScanApplication;
import com.kaiyikang.scan.custom.annotation.CustomAnnotationBean;
import com.kaiyikang.scan.destroy.AnnotationDestroyBean;
import com.kaiyikang.scan.destroy.SpecifyDestroyBean;
import com.kaiyikang.scan.init.AnnotationInitBean;
import com.kaiyikang.scan.init.SpecifyInitBean;
import com.kaiyikang.scan.nested.OuterBean;
import com.kaiyikang.scan.nested.OuterBean.NestedBean;
import com.kaiyikang.scan.primary.CatBean;
import com.kaiyikang.scan.primary.PersonBean;
import com.kaiyikang.scan.primary.StudentBean;
import com.kaiyikang.scan.primary.TeacherBean;
import com.kaiyikang.scan.proxy.InjectProxyOnConstructorBean;
import com.kaiyikang.scan.proxy.InjectProxyOnPropertyBean;
import com.kaiyikang.scan.proxy.OriginBean;
import com.kaiyikang.scan.proxy.SecondProxyBean;
import com.kaiyikang.winter.io.PropertyResolver;

public class AnnotationConfigApplicationContextTest {

    AnnotationConfigApplicationContext ctx;

    @BeforeEach
    void setup() {
        ctx = new AnnotationConfigApplicationContext(ScanApplication.class,
                createPropertyResolver());
    }

    @Test
    public void proxyBean() {
        // Should be proxy instead of origin
        OriginBean proxy = ctx.getBean(OriginBean.class);
        assertSame(SecondProxyBean.class, proxy.getClass());
        assertEquals("Scan App", proxy.getName());
        assertEquals("v1.0", proxy.getVersion());

        assertNull(proxy.name);
        assertNull(proxy.version);

        var inject1 = ctx.getBean(InjectProxyOnPropertyBean.class);
        var inject2 = ctx.getBean(InjectProxyOnConstructorBean.class);
        assertSame(proxy, inject1.injected);
        assertEquals(proxy, inject2.injected);
    }

    @Test
    public void customAnnotationBean() {
        // Find @CustomAnnotation
        assertNotNull(ctx.findBeanDefinition(CustomAnnotationBean.class));
        assertNotNull(ctx.findBeanDefinition("customAnnotation"));
        assertNotNull(ctx.getBean(CustomAnnotationBean.class));
        assertNotNull(ctx.getBean("customAnnotation"));
    }

    @Test
    public void initMethod() {

        // Test @PostConstruct
        var bean1 = ctx.getBean(AnnotationInitBean.class);
        assertEquals("Scan App / v1.0", bean1.appName);

        // Test
        var bean2 = ctx.getBean(SpecifyInitBean.class);
        assertEquals("Scan App / v1.0", bean2.appName);
    }

    @Test
    public void DestroyMethod() {
        AnnotationDestroyBean bean1 = null;
        SpecifyDestroyBean bean2 = null;
        try (var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver())) {
            // test @PreDestroy:
            bean1 = ctx.getBean(AnnotationDestroyBean.class);
            bean2 = ctx.getBean(SpecifyDestroyBean.class);
            assertEquals("Scan App", bean1.appTitle);
            assertEquals("Scan App", bean2.appTitle);
        }
        assertNull(bean1.appTitle);
        assertNull(bean2.appTitle);
    }

    @Test
    public void importedBean() {
        // Find imported components
        assertNotNull(ctx.findBeanDefinition(LocalDateConfiguration.class));
        assertNotNull(ctx.findBeanDefinition("startLocalDate"));
        assertNotNull(ctx.findBeanDefinition("startLocalDateTime"));
        assertNotNull(ctx.findBeanDefinition(ZonedDateConfiguration.class));
        assertNotNull(ctx.findBeanDefinition("startZonedDateTime"));
        assertNotNull(ctx.getBean(LocalDateConfiguration.class));
        assertNotNull(ctx.getBean("startLocalDate"));
        assertNotNull(ctx.getBean("startLocalDateTime"));
        assertNotNull(ctx.getBean(ZonedDateConfiguration.class));
        assertNotNull(ctx.getBean("startZonedDateTime"));

    }

    @Test
    public void NestedBean() {
        // Find nested component
        assertNotNull(ctx.findBeanDefinition(OuterBean.class));
        assertNotNull(ctx.findBeanDefinition(NestedBean.class));
        ctx.getBean(OuterBean.class);
        ctx.getBean(NestedBean.class);
    }

    @Test
    public void findBeanDefinition_findComponentWithPrimary() {
        // Primary order
        BeanDefinition studentDef = ctx.findBeanDefinition(StudentBean.class);
        BeanDefinition teacherDef = ctx.findBeanDefinition(TeacherBean.class);
        List<BeanDefinition> defs = ctx.findBeanDefinitions(PersonBean.class);

        assertTrue(defs.size() == 2);
        assertSame(studentDef, defs.get(0));
        assertSame(teacherDef, defs.get(1));

        // Find primary only
        BeanDefinition personPrimaryDef = ctx.findBeanDefinition(PersonBean.class);
        assertSame(teacherDef, personPrimaryDef);

        var cat = ctx.getBean(CatBean.class);
        assertEquals("Mimi", cat.type);
    }

    PropertyResolver createPropertyResolver() {
        var ps = new Properties();
        ps.put("app.title", "Scan App");
        ps.put("app.version", "v1.0");
        ps.put("jdbc.url", "jdbc:hsqldb:file:testdb.tmp");
        ps.put("jdbc.username", "sa");
        ps.put("jdbc.password", "");
        ps.put("convert.boolean", "true");
        ps.put("convert.byte", "123");
        ps.put("convert.short", "12345");
        ps.put("convert.integer", "1234567");
        ps.put("convert.long", "123456789000");
        ps.put("convert.float", "12345.6789");
        ps.put("convert.double", "123456789.87654321");
        ps.put("convert.localdate", "2023-03-29");
        ps.put("convert.localtime", "20:45:01");
        ps.put("convert.localdatetime", "2023-03-29T20:45:01");
        ps.put("convert.zoneddatetime", "2023-03-29T20:45:01+08:00[Asia/Shanghai]");
        ps.put("convert.duration", "P2DT3H4M");
        ps.put("convert.zoneid", "Asia/Shanghai");
        var pr = new PropertyResolver(ps);
        return pr;
    }

}
