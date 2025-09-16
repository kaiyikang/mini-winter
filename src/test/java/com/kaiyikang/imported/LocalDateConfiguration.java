package com.kaiyikang.imported;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Configuration;

@Configuration
public class LocalDateConfiguration {
    @Bean
    LocalDate startLocalDate() {
        return LocalDate.now();
    }

    @Bean
    LocalDateTime startLocalDateTime() {
        return LocalDateTime.now();
    }
}
