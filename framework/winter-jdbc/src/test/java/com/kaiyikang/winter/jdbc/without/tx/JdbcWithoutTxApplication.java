package com.kaiyikang.winter.jdbc.without.tx;

import javax.sql.DataSource;

import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.ComponentScan;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Value;
import com.kaiyikang.winter.jdbc.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@ComponentScan
@Configuration
public class JdbcWithoutTxApplication {

    @Bean(destroyMethod = "close")
    DataSource dataSource(
            @Value("${winter.datasource.url}") String url,
            @Value("${winter.datasource.username}") String username,
            @Value("${winter.datasource.password}") String password,
            @Value("${winter.datasource.driver-class-name:}") String driver,
            @Value("${winter.datasource.maximum-pool-size:20}") int maximumPoolSize,
            @Value("${winter.datasource.minimum-pool-size:1}") int minimumPoolSize,
            @Value("${winter.datasource.connection-timeout:30000}") int connTimeout) {
        var config = new HikariConfig();
        config.setAutoCommit(false);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        if (driver != null) {
            config.setDriverClassName(driver);
        }
        config.setMaximumPoolSize(minimumPoolSize);
        config.setMinimumIdle(connTimeout);
        config.setConnectionTimeout(connTimeout);
        return new HikariDataSource(config);
    }

    @Bean
    JdbcTemplate jdbcTemplate(@Autowired DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
