package com.paralumo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paralumo.IntegrationTest;
import com.paralumo.config.SessionTokens;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
                    .content(credentials("  Learner@Example.com  ", PASSWORD)))
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
    mvc.perform(
            get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "bearer " + session.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("learner@example.com"));
    mvc.perform(
            get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "BEARER   " + session.getValue() + "  "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("learner@example.com"));
    // An explicit Bearer header takes precedence over a cookie and never falls back on failure.
    mvc.perform(
            get("/api/auth/me").cookie(session).header(HttpHeaders.AUTHORIZATION, "bearer forged"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me").cookie(session).header(HttpHeaders.AUTHORIZATION, "Bearer   "))
        .andExpect(status().isUnauthorized());
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
  void meReturnsOnlyTheAuthenticatedUser() throws Exception {
    MvcResult aliceRegistered =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials("alice@example.com", PASSWORD)))
            .andExpect(status().isCreated())
            .andReturn();
    MvcResult bobRegistered =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials("bob@example.com", PASSWORD)))
            .andExpect(status().isCreated())
            .andReturn();
    String aliceId = read(aliceRegistered, "$.id");
    String bobId = read(bobRegistered, "$.id");
    assertThat(aliceId).isNotEqualTo(bobId);
    Cookie alice = sessionCookie(aliceRegistered);
    Cookie bob = sessionCookie(bobRegistered);

    mvc.perform(get("/api/auth/me").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(aliceId))
        .andExpect(jsonPath("$.email").value("alice@example.com"));
    mvc.perform(get("/api/auth/me").cookie(bob))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(bobId))
        .andExpect(jsonPath("$.email").value("bob@example.com"));
    mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(aliceId));
    mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + bob.getValue()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(bobId));
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
  void registeringWhileAnotherRequestRegistersTheSameEmailIs409() throws Exception {
    try (Connection other = dataSource.getConnection()) {
      other.setAutoCommit(false);
      try (PreparedStatement insert =
          other.prepareStatement(
              "INSERT INTO users (id, email, password_hash, created_at)"
                  + " VALUES (?, 'learner@example.com', 'hash', now())")) {
        insert.setObject(1, UUID.randomUUID());
        assertThat(insert.executeUpdate()).isOne();
      }

      CompletableFuture<MvcResult> registering =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return mvc.perform(
                          post("/api/auth/register")
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(credentials("LEARNER@example.com", PASSWORD)))
                      .andReturn();
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });
      awaitABlockedQuery();
      other.commit();

      MvcResult result = registering.get(10, TimeUnit.SECONDS);
      assertThat(result.getResponse().getStatus()).isEqualTo(409);
      assertThat(read(result, "$.detail")).isEqualTo("A user with this email already exists");
      assertThat(result.getResponse().getCookie(SessionTokens.COOKIE_NAME)).isNull();
    }
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
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("   ", PASSWORD)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("email is required"));
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void passwordsLongerThanBcryptAcceptsAreRejected() throws Exception {
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
    // BCrypt.checkpw truncates at 72 bytes; login must still reject a wrong password past byte 72.
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("long@example.com", tooLong)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid email or password"));
  }

  @Test
  void bodiesAnHtmlFormCanSendAreRejected() throws Exception {
    // The CSRF decision (backend/intent.md) relies on this: a cross-site form can post
    // text/plain, form-urlencoded or multipart, never application/json.
    Cookie session = register("learner@example.com");
    for (MediaType formType :
        new MediaType[] {
          MediaType.TEXT_PLAIN, MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA
        }) {
      mvc.perform(
              post("/api/auth/login")
                  .contentType(formType)
                  .content(credentials("learner@example.com", PASSWORD)))
          .andExpect(status().isUnsupportedMediaType())
          .andExpect(jsonPath("$.detail").isString());
      mvc.perform(
              post("/api/cards")
                  .cookie(session)
                  .contentType(formType)
                  .content("{\"sense_id\":\"x\"}"))
          .andExpect(status().isUnsupportedMediaType());
    }
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
