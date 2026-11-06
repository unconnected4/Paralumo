package com.paralumo.api;

import com.paralumo.api.Dtos.LoginRequest;
import com.paralumo.api.Dtos.RegisterRequest;
import com.paralumo.api.Dtos.UserDto;
import com.paralumo.config.CurrentUser;
import com.paralumo.config.SessionTokens;
import com.paralumo.persistence.UserEntity;
import com.paralumo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email + password accounts. Signing in or registering sets the httpOnly session cookie; the
 * response body is the user.
 */
@RestController
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
class AuthController {

  private final UserService userService;
  private final SessionTokens tokens;

  AuthController(UserService userService, SessionTokens tokens) {
    this.userService = userService;
    this.tokens = tokens;
  }

  @Operation(operationId = "register", summary = "Create an account and sign in")
  @ApiResponse(responseCode = "201", description = "Registered and signed in")
  @ApiResponse(responseCode = "409", description = "Email already registered")
  @ApiResponse(responseCode = "422", description = "Missing or invalid email or password")
  @PostMapping("/register")
  ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
    UserEntity user =
        userService.register(
            Objects.requireNonNull(request.email(), "validated"),
            Objects.requireNonNull(request.password(), "validated"));
    return signedIn(HttpStatus.CREATED, user);
  }

  @Operation(operationId = "login", summary = "Sign in")
  @ApiResponse(responseCode = "200", description = "Signed in")
  @ApiResponse(responseCode = "401", description = "Unknown email or wrong password")
  @ApiResponse(responseCode = "422", description = "Missing email or password")
  @PostMapping("/login")
  ResponseEntity<UserDto> login(@Valid @RequestBody LoginRequest request) {
    UserEntity user =
        userService
            .authenticate(
                Objects.requireNonNull(request.email(), "validated"),
                Objects.requireNonNull(request.password(), "validated"))
            .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    return signedIn(HttpStatus.OK, user);
  }

  @Operation(operationId = "logout", summary = "Sign out (clears the session cookie)")
  @ApiResponse(responseCode = "204", description = "Signed out")
  @PostMapping("/logout")
  ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, tokens.clearingCookie().toString())
        .build();
  }

  @Operation(operationId = "getMe", summary = "The signed-in user")
  @ApiResponse(responseCode = "200", description = "Signed in")
  @ApiResponse(responseCode = "401", description = "Not signed in")
  @GetMapping("/me")
  UserDto me(@AuthenticationPrincipal @Nullable CurrentUser current) {
    if (current == null) {
      throw new UnauthorizedException("Not authenticated");
    }
    return userService
        .find(current.id())
        .map(AuthController::toDto)
        .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
  }

  private ResponseEntity<UserDto> signedIn(HttpStatus status, UserEntity user) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, tokens.cookie(tokens.issue(user.getId())).toString())
        .body(toDto(user));
  }

  private static UserDto toDto(UserEntity user) {
    return new UserDto(user.getId().toString(), user.getEmail());
  }
}
