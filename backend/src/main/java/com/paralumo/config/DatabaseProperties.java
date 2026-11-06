package com.paralumo.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Postgres connection: {@code DATABASE_URL}, {@code DATABASE_USER}, {@code DATABASE_PASSWORD}. */
@Validated
@ConfigurationProperties("paralumo.database")
public record DatabaseProperties(@NotBlank String url, @NotBlank String user, String password) {}
