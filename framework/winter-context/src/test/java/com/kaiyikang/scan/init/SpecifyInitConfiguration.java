package com.kaiyikang.scan.init;

import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Value;

@Configuration
public class SpecifyInitConfiguration {

    @Bean(initMethod = "init")
    SpecifyInitBean createSpecifyInitBean(@Value("${app.title}") String appTitle,
            @Value("${app.version}") String appVersion) {
        return new SpecifyInitBean(appTitle, appVersion);
    }
}
