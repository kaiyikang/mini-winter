package com.kaiyikang.winter.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Objects;

import jakarta.annotation.Nullable;

public class BeanDefinition implements Comparable<BeanDefinition> {

    // Global only bean name
    private final String name;

    // Declared Type of bean. Note: not the actual type
    private final Class<?> beanClass;

    // Instance
    private Object instance = null;

    // Constructor/null
    private final Constructor<?> constructor;

    // Name of factory
    private final String factoryName;

    // Method of factory
    private final Method factoryMethod;

    // Order
    private final int order;

    // Primary annotation
    private final boolean primary;

    private String initMethodName;
    private String destroyMethodName;

    private Method initMethod;
    private Method destroyMethod;

    public BeanDefinition(String name,
            Class<?> beanClass,
            Constructor<?> constructor,
            int order, boolean primary,
            String initMethodName,
            String destroyMethodName,
            Method initMethod,
            Method destroyMethod) {
        this.name = name;
        this.beanClass = beanClass;
        this.constructor = constructor;
        this.factoryName = null;
        this.factoryMethod = null;
        this.order = order;
        this.primary = primary;
        constructor.setAccessible(true);
        setInitAndDestroyMethod(initMethodName, destroyMethodName, initMethod, destroyMethod);
    }

    public BeanDefinition(String name, Class<?> beanClass, String factoryName, Method factoryMethod, int order,
            boolean primary, String initMethodName,
            String destroyMethodName, Method initMethod, Method destroyMethod) {
        this.name = name;
        this.beanClass = beanClass;
        this.constructor = null;
        this.factoryName = factoryName;
        this.factoryMethod = factoryMethod;
        this.order = order;
        this.primary = primary;
        factoryMethod.setAccessible(true);
        setInitAndDestroyMethod(initMethodName, destroyMethodName, initMethod, destroyMethod);
    }

    private void setInitAndDestroyMethod(String initMethodName, String destroyMethodName, Method initMethod,
            Method destroyMethod) {
        this.initMethodName = initMethodName;
        this.destroyMethodName = destroyMethodName;
        if (initMethod != null) {
            initMethod.setAccessible(true);
        }
        if (destroyMethod != null) {
            initMethod.setAccessible(true);
        }
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }

    @Nullable
    public Constructor<?> getConstructor() {
        return this.constructor;
    }

    @Nullable
    public String getFactoryName() {
        return this.factoryName;
    }

    @Nullable
    public Method getFactoryMethod() {
        return this.factoryMethod;
    }

    @Nullable
    public Method getInitMethod() {
        return this.initMethod;
    }

    @Nullable
    public Method getDestroyMethod() {
        return this.destroyMethod;
    }

    @Nullable
    public String getInitMethodName() {
        return this.initMethodName;
    }

    @Nullable
    public String getDestroyMethodName() {
        return this.destroyMethodName;
    }

    public String getName() {
        return this.name;
    }

    public Class<?> getBeanClass() {
        return this.beanClass;
    }

    @Nullable
    public Object getInstance() {
        return this.instance;
    }

    public Object getRequiredInstance() {
        if (this.instance == null) {
            // TODO
        }
        return this.instance;
    }

    public void setInstance(Object instance) {
        Objects.requireNonNull(instance, "Bean instance is null.");
        if (!this.beanClass.isAssignableFrom(instance.getClass())) {
            // TODO
        }
        this.instance = instance;
    }

    public boolean isPrimary() {
        return this.primary;
    }

    @Override
    public int compareTo(BeanDefinition o) {
        int cmp = Integer.compare(this.order, o.order);
        if (cmp != 0) {
            return cmp;
        }
        return this.name.compareTo(o.name);
    }

}
