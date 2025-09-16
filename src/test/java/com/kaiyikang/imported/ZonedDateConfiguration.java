package com.kaiyikang.imported;

import java.time.ZonedDateTime;

import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Configuration;

@Configuration
public class ZonedDateConfiguration {

    @Bean
    ZonedDateTime startZonedDateTime() {
        return ZonedDateTime.now();
    }
}
