package com.paralumo.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code AUTH_SECRET} signs the session JWT (HMAC-SHA256), so it must be at least 32 bytes long.
 */
@Validated
@ConfigurationProperties("paralumo.auth")
public record AuthProperties(@NotBlank @Size(min = 32) String secret) {}
