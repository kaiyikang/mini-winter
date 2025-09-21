package com.kaiyikang.scan.proxy;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.annotation.Order;

@Order(100)
@Component
public class FirstProxyBeanPostProcessor implements BeanPostProcessor {
    final Logger logger = LoggerFactory.getLogger(getClass());

    Map<String, Object> originBeans = new HashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (OriginBean.class.isAssignableFrom(bean.getClass())) {
            logger.debug("create first proxy for bean '{}': {}", beanName, bean);
            var proxy = new FirstProxyBean((OriginBean) bean);
            originBeans.put(beanName, bean);
            return proxy;
        }
        return bean;
    }

    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName) {
        Object origin = originBeans.get(beanName);
        if (origin != null) {
            logger.debug("auto set property for {} from first proxy {} to origin bean: ");
            return origin;
        }
        return bean;
    }
}
