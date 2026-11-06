package com.paralumo.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code CORS_ORIGINS}: comma-separated origins allowed to call the API cross-origin. Empty
 * locally, where the Vite proxy makes the web app same-origin.
 */
@ConfigurationProperties("paralumo.cors")
public record CorsProperties(List<String> origins) {
  public CorsProperties {
    origins =
        origins == null
            ? List.of()
            : origins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
    if (origins.contains("*")) {
      throw new IllegalArgumentException(
          "CORS_ORIGINS must list explicit origins, not '*' (credentials are enabled)");
    }
  }
}
