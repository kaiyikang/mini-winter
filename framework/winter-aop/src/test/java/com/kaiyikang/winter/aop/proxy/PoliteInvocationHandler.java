package com.kaiyikang.winter.aop.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class PoliteInvocationHandler implements InvocationHandler {

    @Override
    public Object invoke(Object bean, Method method, Object[] args) throws Throwable {
        // Interceptor: filter the method with @Polite
        if (method.getAnnotation(Polite.class) != null) {
            String ret = (String) method.invoke(bean, args);
            // Advice: and it is "Around Advice"
            // Since it controls start and end
            if (ret.endsWith(".")) {
                ret = ret.substring(0, ret.length() - 1) + "!";
            }
            return ret;
        }
        return method.invoke(bean, args);
    }
}
