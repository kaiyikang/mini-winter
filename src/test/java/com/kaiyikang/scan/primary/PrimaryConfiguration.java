package com.kaiyikang.scan.primary;

import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Primary;

@Configuration
public class PrimaryConfiguration {

    @Primary
    @Bean
    CatBean mimi() {
        return new CatBean("Mimi");
    }

    @Bean
    CatBean bibi() {
        return new CatBean("Bibi");
    }
}