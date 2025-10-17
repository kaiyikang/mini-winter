package com.kaiyikang.winter.aop;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import com.kaiyikang.winter.context.ApplicationContextUtils;
import com.kaiyikang.winter.context.BeanDefinition;
import com.kaiyikang.winter.context.BeanPostProcessor;
import com.kaiyikang.winter.context.ConfigurableApplicationContext;
import com.kaiyikang.winter.exception.AopConfigException;
import com.kaiyikang.winter.exception.BeansException;

public class AnnotationProxyBeanPostProcessor<A extends Annotation> implements BeanPostProcessor {

    Map<String, Object> originBeans = new HashMap<>();
    Class<A> annotationClass;

    public AnnotationProxyBeanPostProcessor() {
        this.annotationClass = getParameterizedType();
    }

    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();

        A anno = beanClass.getAnnotation(annotationClass);
        if (anno == null) {
            return bean;
        }

        String handlerName;
        try {
            handlerName = (String) anno.annotationType().getMethod("value").invoke(anno);
        } catch (ReflectiveOperationException e) {
            throw new AopConfigException(
                    String.format("@%s must have value() returned String type.", this.annotationClass.getSimpleName()),
                    e);
        }
        Object proxy = createProxy(beanClass, bean, handlerName);
        originBeans.put(beanName, bean);
        return proxy;
    }

    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName) {
        Object origin = originBeans.get(beanName);
        return origin != null ? origin : bean;
    }

    private Object createProxy(Class<?> beanClass, Object bean, String handlerName) {
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) ApplicationContextUtils
                .getRequiredApplicationContext();

        BeanDefinition handlerBeanDef = ctx.findBeanDefinition(handlerName);
        if (handlerBeanDef == null) {
            throw new AopConfigException(String.format("@%s proxy handler '%s' not found.",
                    this.annotationClass.getSimpleName(), handlerName));
        }

        Object handlerBean = handlerBeanDef.getInstance();
        if (handlerBean == null) {
            handlerBean = ctx.createBeanAsEarlySingleton(handlerBeanDef);
        }

        if (handlerBean instanceof InvocationHandler handler) {
            return ProxyResolver.getInstance().createProxy(bean, handler);
        } else {
            throw new AopConfigException(String.format("@%s proxy handler '%s' is not type of %s.",
                    this.annotationClass.getSimpleName(), handlerName,
                    InvocationHandler.class.getName()));
        }

    }

    @SuppressWarnings("unchecked")
    private Class<A> getParameterizedType() {
        Type type = getClass().getGenericSuperclass();
        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException("Class " + getClass().getName() + " does not have parameterized type.");
        }

        ParameterizedType pt = (ParameterizedType) type;
        Type[] types = pt.getActualTypeArguments();
        if (types.length != 1) {
            throw new IllegalArgumentException(
                    "Class " + getClass().getName() + " has more than 1 parameterized types.");
        }

        Type r = types[0];
        if (!(r instanceof Class<?>)) {
            throw new IllegalArgumentException(
                    "Class " + getClass().getName() + " does not have parameterized type of class.");
        }

        return (Class<A>) r;
    }
}
