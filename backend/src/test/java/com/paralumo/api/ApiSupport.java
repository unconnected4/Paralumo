package com.paralumo.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.paralumo.config.SessionTokens;
import jakarta.servlet.http.Cookie;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Shared setup for API integration tests: an empty database and helpers to sign users in. */
abstract class ApiSupport {

  static final String PASSWORD = "password123";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void emptyDatabase() {
    jdbc.execute("TRUNCATE user_cards, senses, lexemes, users");
  }

  /** Registers a user and returns their session cookie. */
  Cookie register(String email) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email, PASSWORD)))
            .andExpect(status().isCreated())
            .andReturn();
    return sessionCookie(result);
  }

  static Cookie sessionCookie(MvcResult result) {
    return Objects.requireNonNull(
        result.getResponse().getCookie(SessionTokens.COOKIE_NAME), "session cookie");
  }

  static String credentials(String email, String password) {
    return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
  }

  static String read(MvcResult result, String path) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), path);
  }
}
