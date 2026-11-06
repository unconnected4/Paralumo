package com.paralumo.service;

import com.paralumo.persistence.UserEntity;
import com.paralumo.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Accounts: registration and password sign-in. Session tokens are handled by the API layer. */
@Service
public class UserService {

  /** BCrypt, the password hash, only examines the first 72 UTF-8 bytes. */
  public static final int MAX_PASSWORD_BYTES = 72;

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final Clock clock;

  public UserService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      AuthenticationManager authenticationManager,
      Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.authenticationManager = authenticationManager;
    this.clock = clock;
  }

  /** Emails are compared case-insensitively, so they are stored lowercased. */
  public static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  /** Creates an account; an email that is already registered is a {@link ConflictException}. */
  public UserEntity register(String email, String password) {
    String normalized = normalizeEmail(email);
    if (users.existsByEmail(normalized)) {
      throw new ConflictException("A user with this email already exists");
    }
    try {
      return users.saveAndFlush(
          new UserEntity(
              UUID.randomUUID(), normalized, passwordEncoder.encode(password), clock.instant()));
    } catch (DataIntegrityViolationException e) {
      // Registered concurrently between the check and the insert.
      throw new ConflictException("A user with this email already exists");
    }
  }

  /**
   * Checks the password through Spring Security's {@link AuthenticationManager}.
   *
   * @return the user, or empty for an unknown email or a wrong password (deliberately the same)
   */
  public Optional<UserEntity> authenticate(String email, String password) {
    // BCrypt.checkpw silently truncates at 72 UTF-8 bytes, which would accept a wrong password
    // that shares its first 72 bytes with a 72-byte stored password.
    if (password.length() > MAX_PASSWORD_BYTES
        || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      return Optional.empty();
    }
    String normalized = normalizeEmail(email);
    try {
      authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(normalized, password));
    } catch (AuthenticationException e) {
      return Optional.empty();
    }
    return users.findByEmail(normalized);
  }

  public Optional<UserEntity> find(UUID id) {
    return users.findById(id);
  }
}
