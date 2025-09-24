package com.kaiyikang.scan.destroy;

import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Value;

@Configuration
public class SpecifyDestroyConfiguration {

    @Bean(destroyMethod = "destroy")
    SpecifyDestroyBean createSpecifyDestroyBean(@Value("${app.title}") String appTitle) {
        return new SpecifyDestroyBean(appTitle);
    }

}
