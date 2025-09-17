package com.kaiyikang.winter.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.kaiyikang.scan.nested.OuterBean;
import com.kaiyikang.scan.nested.OuterBean.NestedBean;
import com.kaiyikang.scan.primary.CatBean;
import com.kaiyikang.scan.primary.PersonBean;
import com.kaiyikang.scan.primary.StudentBean;
import com.kaiyikang.scan.primary.TeacherBean;
import com.kaiyikang.winter.io.PropertyResolver;

public class AnnotationConfigApplicationContextTest {

    AnnotationConfigApplicationContext ctx;

    @BeforeEach
    void setup() {
        ctx = new AnnotationConfigApplicationContext(ScanApplication.class,
                createPropertyResolver());
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

    @Test
    public void testAnnotationConfigApplicationContext() {

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
