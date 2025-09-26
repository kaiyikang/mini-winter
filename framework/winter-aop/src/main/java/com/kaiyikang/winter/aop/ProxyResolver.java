package com.kaiyikang.winter.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import javax.management.RuntimeErrorException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public class ProxyResolver {

    final Logger logger = LoggerFactory.getLogger(getClass());

    final ByteBuddy byteBuddy = new ByteBuddy();

    @SuppressWarnings("unchecked")
    public <T> T createProxy(T bean, InvocationHandler handler) {

        Class<?> targetClass = bean.getClass();
        logger.atDebug().log("create proxy for bean {} @{}", targetClass.getName(),
                Integer.toHexString(bean.hashCode()));

        Class<?> proxyClass = this.byteBuddy
                // create subclass of the targetClass since the subclass can inherit the class.
                // and with no args constructor
                .subclass(targetClass, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
                // intercept which type of method, or use named("hello") only for hello()
                .method(ElementMatchers.isPublic())
                // main logic
                .intercept(InvocationHandlerAdapter.of(
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                // avoid the dead loop, here use bean
                                return handler.invoke(bean, method, args);
                            }
                        }))
                // generate .class
                .make()
                // load into loader
                .load(targetClass.getClassLoader())
                // get the loaded class
                .getLoaded();

        Object proxy;
        try {
            proxy = proxyClass.getConstructor().newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return (T) proxy;
    }
}
