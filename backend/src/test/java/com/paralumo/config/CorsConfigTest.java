package com.paralumo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CorsFilter;

class CorsConfigTest {

  @RestController
  static class PingController {
    @GetMapping("/api/ping")
    Map<String, String> ping() {
      return Map.of("status", "ok");
    }
  }

  @Test
  void trimsOriginsAndDropsBlanks() {
    CorsProperties cors =
        new CorsProperties(
            List.of(" https://app.example.com ", "", "   ", "https://b.example.com"));

    assertThat(cors.origins()).containsExactly("https://app.example.com", "https://b.example.com");
  }

  @Test
  void rejectsWildcardOriginBecauseCredentialsAreEnabled() {
    assertThatThrownBy(() -> new CorsProperties(List.of("*")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CORS_ORIGINS");
    assertThatThrownBy(() -> new CorsProperties(List.of("https://app.example.com", " * ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allowsListedOriginWithCredentialsAndRejectsUnlistedOrigins() throws Exception {
    CorsProperties cors = new CorsProperties(List.of("https://app.example.com"));
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new PingController())
            .addFilters(new CorsFilter(new SecurityConfig().corsConfigurationSource(cors)))
            .build();

    mvc.perform(
            options("/api/ping")
                .header(HttpHeaders.ORIGIN, "https://app.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.com"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

    mvc.perform(get("/api/ping").header(HttpHeaders.ORIGIN, "https://evil.example.com"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }
}
