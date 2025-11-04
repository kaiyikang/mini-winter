package com.kaiyikang.winter.jdbc.with.tx;

import com.kaiyikang.winter.annotation.ComponentScan;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Import;
import com.kaiyikang.winter.jdbc.JdbcConfiguration;

@ComponentScan
@Configuration
@Import(JdbcConfiguration.class)
public class JdbcWithTxApplication {

}
