package com.kaiyikang.winter.aop.before;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.aop.BeforeInvocationHandlerAdapter;

@Component
public class LogInvocationHandler extends BeforeInvocationHandlerAdapter {
    final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void before(Object proxy, Method method, Object[] args) {
        logger.info("[before] {}()", method.getName());
    }
}
