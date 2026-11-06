package com.paralumo.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Issues and checks the session token: one HMAC-signed JWT whose subject is the user id. The web
 * app carries it in an httpOnly cookie; other clients can send it as {@code Authorization: Bearer}.
 */
@Component
public class SessionTokens {

  public static final String COOKIE_NAME = "paralumo_session";
  static final Duration LIFETIME = Duration.ofDays(30);

  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final Clock clock;
  private final boolean secureCookie;

  public SessionTokens(AuthProperties auth, AppProperties app, Clock clock) {
    SecretKey key = new SecretKeySpec(auth.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    this.clock = clock;
    this.secureCookie = app.isProduction();
  }

  public String issue(UUID userId) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(LIFETIME))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /** The user a token belongs to, or empty if it is malformed, forged or expired. */
  public Optional<UUID> verify(String token) {
    try {
      @Nullable String subject = decoder.decode(token).getSubject();
      return subject == null ? Optional.empty() : Optional.of(UUID.fromString(subject));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public ResponseCookie cookie(String token) {
    return baseCookie(token).maxAge(LIFETIME).build();
  }

  public ResponseCookie clearingCookie() {
    return baseCookie("").maxAge(Duration.ZERO).build();
  }

  // SameSite=Lax keeps the browser from sending the cookie on cross-site POST/DELETE, which is
  // the CSRF protection for the cookie (see SecurityConfig).
  private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Lax")
        .path("/api");
  }
}
