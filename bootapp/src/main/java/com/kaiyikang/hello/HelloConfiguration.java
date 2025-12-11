package com.kaiyikang.hello;

import com.kaiyikang.winter.annotation.ComponentScan;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Import;
import com.kaiyikang.winter.jdbc.JdbcConfiguration;
import com.kaiyikang.winter.web.WebMvcConfiguration;

@ComponentScan
@Configuration
@Import({ JdbcConfiguration.class, WebMvcConfiguration.class })
public class HelloConfiguration {

}
