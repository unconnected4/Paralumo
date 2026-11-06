package com.paralumo.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.paralumo.service.UserService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Request and response bodies. Field names become snake_case on the wire (global Jackson naming
 * strategy). {@code @Schema(requiredMode = REQUIRED)} marks what the spec, and so the generated web
 * types, treat as always present; null fields are omitted from responses.
 */
final class Dtos {

  private Dtos() {}

  @Schema(name = "ExampleSentence")
  record ExampleSentenceDto(
      @Schema(requiredMode = REQUIRED) String en, @Schema(requiredMode = REQUIRED) String ru) {}

  @Schema(name = "Sense")
  record SenseDto(
      @Schema(requiredMode = REQUIRED, description = "Opaque sense id; pass it to POST /api/cards")
          String id,
      @Schema(requiredMode = REQUIRED) String translationRu,
      List<String> synonymsRu,
      List<String> meaningsEn,
      List<ExampleSentenceDto> examples,
      @Schema(
              requiredMode = REQUIRED,
              description = "Whether the current user has saved this sense (false when signed out)")
          boolean saved) {}

  @Schema(name = "Lexeme")
  record LexemeDto(
      @Schema(requiredMode = REQUIRED) String lemma,
      @Schema(requiredMode = REQUIRED, example = "verb") String pos,
      @Nullable String transcription,
      @Schema(requiredMode = REQUIRED) List<SenseDto> senses) {}

  @Schema(name = "LookupResponse")
  record LookupResponse(
      @Schema(requiredMode = REQUIRED) String query,
      @Schema(requiredMode = REQUIRED) List<LexemeDto> lexemes) {}

  @Schema(name = "Card")
  record CardDto(
      @Schema(requiredMode = REQUIRED) String id,
      @Schema(requiredMode = REQUIRED) String senseId,
      @Schema(requiredMode = REQUIRED) String lemma,
      @Schema(requiredMode = REQUIRED) String pos,
      @Nullable String transcription,
      @Schema(requiredMode = REQUIRED) String translationRu,
      List<String> synonymsRu,
      List<String> meaningsEn,
      List<ExampleSentenceDto> examples,
      @Schema(requiredMode = REQUIRED) Instant createdAt,
      @Nullable String notes) {}

  @Schema(name = "SaveCardRequest")
  record SaveCardRequest(
      @Schema(requiredMode = REQUIRED) @NotBlank(message = "sense_id is required")
          @Nullable String senseId) {}

  @Schema(name = "User")
  record UserDto(
      @Schema(requiredMode = REQUIRED) String id, @Schema(requiredMode = REQUIRED) String email) {}

  @Schema(name = "LoginRequest")
  record LoginRequest(
      @Schema(requiredMode = REQUIRED) @NotBlank(message = "email is required")
          @Nullable String email,
      @Schema(requiredMode = REQUIRED) @NotBlank(message = "password is required")
          @Nullable String password) {}

  @Schema(name = "RegisterRequest")
  record RegisterRequest(
      @Schema(requiredMode = REQUIRED)
          @NotBlank(message = "email is required")
          @Email(message = "email must be a valid email address")
          @Nullable String email,
      @Schema(requiredMode = REQUIRED)
          @NotBlank(message = "password is required")
          @Size(min = 8, message = "password must be at least 8 characters")
          // BCrypt, the password hash, refuses longer input.
          @MaxUtf8Bytes(
              value = UserService.MAX_PASSWORD_BYTES,
              message = "password must be at most 72 bytes")
          @Nullable String password) {}
}
