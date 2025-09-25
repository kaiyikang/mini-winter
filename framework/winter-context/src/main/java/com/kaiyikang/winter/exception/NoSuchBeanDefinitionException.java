package com.kaiyikang.winter.exception;

public class NoSuchBeanDefinitionException extends BeansException {

    public NoSuchBeanDefinitionException() {
    }

    public NoSuchBeanDefinitionException(String message) {
        super(message);
    }
}
