package io.github.mkliszczun.fridge.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    /**
     * Injects changed value
     * If not found - app will not run
     */
    @Value("${DATABASE_URL}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        String jdbcUrl = databaseUrl;


        if (databaseUrl != null && databaseUrl.startsWith("postgresql://")) {
            jdbcUrl = "jdbc:" + databaseUrl;
        }

        return DataSourceBuilder.create()
                .url(jdbcUrl)
                .build();
    }
}
