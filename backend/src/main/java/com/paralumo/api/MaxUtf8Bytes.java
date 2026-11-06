package com.paralumo.api;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * The string is at most {@link #value} bytes in UTF-8. {@code @Size} counts characters, which isn't
 * enough where the limit is in bytes (BCrypt rejects passwords over 72 bytes, and a Cyrillic letter
 * takes two). Null is valid; combine with {@code @NotBlank} to require a value.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
@interface MaxUtf8Bytes {

  int value();

  String message();

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {

    private int max;

    @Override
    public void initialize(MaxUtf8Bytes annotation) {
      max = annotation.value();
    }

    @Override
    public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
      return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
  }
}
