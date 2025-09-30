package com.kaiyikang.winter.aop.around;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import com.kaiyikang.winter.annotation.Component;

@Component
public class AroundInvocationHandler implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if (method.getAnnotation(Polite.class) != null) {
            String ret = (String) method.invoke(proxy, args);
            if (ret.endsWith(".")) {
                ret = ret.substring(0, ret.length() - 1) + "!";
            }
            return ret;
        }
        return method.invoke(proxy, args);
    }
}
