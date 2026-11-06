package com.paralumo.config;

import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the pool from {@link DatabaseProperties}, the only place database settings are read. */
@Configuration(proxyBeanMethods = false)
class DataSourceConfig {

  @Bean
  DataSource dataSource(DatabaseProperties database) {
    return DataSourceBuilder.create()
        .url(database.url())
        .username(database.user())
        .password(database.password())
        .build();
  }
}
