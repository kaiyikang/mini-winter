package com.kaiyikang.winter.context;

public interface BeanPostProcessor {

    /**
     * Invoked after new Bean()
     */
    default Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    /*
     * Invoked after bean.init()
     */
    default Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;
    }

    /*
     * Invoked before bean.setABC()
     */
    default Object postProcessOnSetProperty(Object bean, String beanName) {
        return bean;
    }
}
