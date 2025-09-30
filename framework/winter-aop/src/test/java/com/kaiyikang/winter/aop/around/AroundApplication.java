package com.kaiyikang.winter.aop.around;

import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.ComponentScan;
import com.kaiyikang.winter.annotation.Configuration;

@Configuration
@ComponentScan
public class AroundApplication {

    @Bean
    AroundProxyBeanPostProcessor crateAroundProxyBeanPostProcessor() {
        return new AroundProxyBeanPostProcessor();
    }
}
