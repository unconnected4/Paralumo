package com.paralumo.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code APP_ENV}: where the app runs. Only production marks the session cookie Secure. */
@Validated
@ConfigurationProperties("paralumo.app")
public record AppProperties(@NotNull Environment env) {

  public enum Environment {
    LOCAL,
    TEST,
    PRODUCTION
  }

  public boolean isProduction() {
    return env == Environment.PRODUCTION;
  }
}
