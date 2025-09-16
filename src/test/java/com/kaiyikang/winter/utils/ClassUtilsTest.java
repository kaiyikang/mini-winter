package com.kaiyikang.winter.utils;

import org.junit.jupiter.api.Test;

import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Order;
import com.kaiyikang.winter.exception.BeanDefinitionException;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

public class ClassUtilsTest {

    @Test
    public void noComponent() throws Exception {
        assertNull(ClassUtils.findAnnotation(Simple.class, Component.class));
    }

    @Test
    public void simpleComponent() throws Exception {
        assertNotNull(ClassUtils.findAnnotation(SimpleComponent.class, Component.class));
        assertEquals("simpleComponent", ClassUtils.getBeanName(SimpleComponent.class));
    }

    @Test
    public void simpleConfiguration() throws Exception {
        assertNotNull(ClassUtils.findAnnotation(SimpleConfiguration.class, Component.class));
        assertEquals("simpleConfiguration", ClassUtils.getBeanName(SimpleConfiguration.class));
    }

    @Test
    public void simpleConfigurationWithName() throws Exception {
        assertNotNull(ClassUtils.findAnnotation(SimpleConfigurationWithName.class, Component.class));
        assertEquals("simpleCfg", ClassUtils.getBeanName(SimpleConfigurationWithName.class));
    }

    @Test
    public void customComponent() throws Exception {
        assertNotNull(ClassUtils.findAnnotation(Custom.class, Component.class));
        assertEquals("custom", ClassUtils.getBeanName(Custom.class));
    }

    @Test
    public void customComponentWithName() throws Exception {
        assertNotNull(ClassUtils.findAnnotation(CustomWithName.class, Component.class));
        assertEquals("customName", ClassUtils.getBeanName(CustomWithName.class));
    }

    @Test
    public void duplicateComponent() throws Exception {
        assertThrows(BeanDefinitionException.class, () -> {
            ClassUtils.findAnnotation(DuplicateComponent.class, Component.class);
        });
        assertThrows(BeanDefinitionException.class, () -> {
            ClassUtils.findAnnotation(DuplicateComponent2.class, Component.class);
        });
    }

    @Test
    public void testWhenMethodHasNoBeanAnnotation() throws NoSuchMethodException {
        Method method = TestBeanConfiguration.class.getMethod("methodWithoutBeanAnnotation");
        String beanName = ClassUtils.getBeanName(method);
        assertEquals("methodWithoutBeanAnnotation", beanName);
    }

    @Test
    public void testWhenMethodHasBeanAnnotationWithEmptyValue() throws NoSuchMethodException {
        Method method = TestBeanConfiguration.class.getMethod("methodWithBeanAnnotationButEmptyValue");
        String beanName = ClassUtils.getBeanName(method);
        assertEquals("methodWithBeanAnnotationButEmptyValue", beanName);
    }

    @Test
    void testWhenMethodHasBeanAnnotationWithValue() throws NoSuchMethodException {
        Method method = TestBeanConfiguration.class.getMethod("methodWithBeanAnnotationAndValue");
        String beanName = ClassUtils.getBeanName(method);
        assertEquals("customBeanName", beanName);
    }
}

@Order(1)
class Simple {
}

@Component
class SimpleComponent {
}

@Component("simpleName")
class SimpleComponentWithName {
}

@Configuration
class SimpleConfiguration {
}

@Configuration("simpleCfg")
class SimpleConfigurationWithName {
}

@CustomComponent
class Custom {
}

@CustomComponent("customName")
class CustomWithName {
}

@Component
@Configuration
class DuplicateComponent {
}

@CustomComponent
@Configuration
class DuplicateComponent2 {
}

class TestBeanConfiguration {

    public String methodWithoutBeanAnnotation() {
        return "method1";
    }

    @Bean
    public String methodWithBeanAnnotationButEmptyValue() {
        return "method2";
    }

    @Bean("customBeanName")
    public String methodWithBeanAnnotationAndValue() {
        return "method3";
    }
}