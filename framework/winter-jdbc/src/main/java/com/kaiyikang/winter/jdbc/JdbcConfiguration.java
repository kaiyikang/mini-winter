package com.kaiyikang.winter.jdbc;

import javax.sql.DataSource;

import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Value;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class JdbcConfiguration {

    @Bean(destroyMethod = "close")
    DataSource dataSrouce(
            @Value("$.{winter.dataSource.url}") String url,
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
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumPoolSize);
        config.setConnectionTimeout(connTimeout);
        return new HikariDataSource(config);
    }

    @Bean
    JdbcTemplate jdbcTemplate(@Autowired DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
