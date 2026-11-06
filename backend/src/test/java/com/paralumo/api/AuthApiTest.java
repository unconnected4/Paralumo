package com.paralumo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paralumo.IntegrationTest;
import com.paralumo.config.SessionTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class AuthApiTest extends ApiSupport {

  @Test
  void registerSignsInAndMeReturnsTheUser() throws Exception {
    MvcResult registered =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials("Learner@Example.com", PASSWORD)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("learner@example.com"))
            .andExpect(jsonPath("$.id").isString())
            .andReturn();
    Cookie session = sessionCookie(registered);
    assertThat(session.isHttpOnly()).isTrue();
    assertThat(registered.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Lax");

    mvc.perform(get("/api/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(read(registered, "$.id")))
        .andExpect(jsonPath("$.email").value("learner@example.com"));
  }

  @Test
  void bearerHeaderWorksLikeTheCookie() throws Exception {
    Cookie session = register("learner@example.com");

    mvc.perform(
            get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("learner@example.com"));
  }

  @Test
  void meIs401WhenSignedOutOrTheTokenIsInvalid() throws Exception {
    mvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Not authenticated"));
    mvc.perform(get("/api/auth/me").cookie(new Cookie(SessionTokens.COOKIE_NAME, "forged")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void duplicateEmailIs409IgnoringCase() throws Exception {
    register("learner@example.com");

    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("LEARNER@example.com", "another-password")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("A user with this email already exists"));
  }

  @Test
  void loginWithTheRightPasswordSignsIn() throws Exception {
    register("learner@example.com");

    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials("learner@example.com", PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("learner@example.com"))
            .andReturn();

    mvc.perform(get("/api/auth/me").cookie(sessionCookie(result))).andExpect(status().isOk());
  }

  @Test
  void loginWithUnknownEmailOrWrongPasswordIs401() throws Exception {
    register("learner@example.com");

    for (String body :
        new String[] {
          credentials("nobody@example.com", PASSWORD), credentials("learner@example.com", "wrong")
        }) {
      mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }
  }

  @Test
  void loginStillWorksWithAStaleCookie() throws Exception {
    register("learner@example.com");

    mvc.perform(
            post("/api/auth/login")
                .cookie(new Cookie(SessionTokens.COOKIE_NAME, "expired-or-forged"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("learner@example.com", PASSWORD)))
        .andExpect(status().isOk());
  }

  @Test
  void missingOrInvalidFieldsAre422() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"learner@example.com\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("password is required"));
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("not-an-email", "short")))
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "email must be a valid email address; password must be at least 8 characters"));
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void passwordsLongerThanBcryptAcceptsAre422() throws Exception {
    // 36 Cyrillic letters: 36 characters, 72 bytes, the most BCrypt takes.
    String longest = "пароль".repeat(6);
    String tooLong = longest + "x";
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("long@example.com", tooLong)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("password must be at most 72 bytes"));
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("long@example.com", longest)))
        .andExpect(status().isCreated());
  }

  @Test
  void logoutClearsTheCookie() throws Exception {
    MvcResult result =
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent()).andReturn();

    Cookie cleared = sessionCookie(result);
    assertThat(cleared.getValue()).isEmpty();
    assertThat(cleared.getMaxAge()).isZero();
  }
}
