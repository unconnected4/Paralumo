package com.paralumo.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.paralumo.config.AppProperties.Environment;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class SessionTokensTest {

  private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";
  private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

  private static SessionTokens tokens(String secret, Instant now, Environment env) {
    return new SessionTokens(
        new AuthProperties(secret), new AppProperties(env), Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void verifiesItsOwnTokens() {
    SessionTokens tokens = tokens(SECRET, NOW, Environment.TEST);
    UUID user = UUID.randomUUID();

    assertThat(tokens.verify(tokens.issue(user))).contains(user);
  }

  @Test
  void rejectsTokensSignedWithAnotherSecret() {
    String foreign =
        tokens("another-secret-that-is-at-least-32-bytes", NOW, Environment.TEST)
            .issue(UUID.randomUUID());

    assertThat(tokens(SECRET, NOW, Environment.TEST).verify(foreign)).isEmpty();
  }

  @Test
  void rejectsExpiredTokens() {
    String old =
        tokens(SECRET, NOW.minus(SessionTokens.LIFETIME).minusSeconds(120), Environment.TEST)
            .issue(UUID.randomUUID());

    assertThat(tokens(SECRET, NOW, Environment.TEST).verify(old)).isEmpty();
  }

  @Test
  void rejectsGarbage() {
    assertThat(tokens(SECRET, NOW, Environment.TEST).verify("not-a-jwt")).isEmpty();
  }

  @Test
  void cookieIsHttpOnlyLaxAndSecureOnlyInProduction() {
    ResponseCookie local = tokens(SECRET, NOW, Environment.LOCAL).cookie("t");
    ResponseCookie production = tokens(SECRET, NOW, Environment.PRODUCTION).cookie("t");

    assertThat(local.isHttpOnly()).isTrue();
    assertThat(local.getSameSite()).isEqualTo("Lax");
    assertThat(local.isSecure()).isFalse();
    assertThat(production.isSecure()).isTrue();
  }
}
