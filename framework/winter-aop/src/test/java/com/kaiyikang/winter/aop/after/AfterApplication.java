package com.kaiyikang.winter.aop.after;

import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.ComponentScan;
import com.kaiyikang.winter.annotation.Configuration;

@Configuration
@ComponentScan
public class AfterApplication {

    @Bean
    AroundProxyBeanPostProcessor createAroundProxyBeanPostProcessor() {
        return new AroundProxyBeanPostProcessor();
    }
}
