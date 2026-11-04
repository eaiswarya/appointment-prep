package com.appointmentprep.auth;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Password rules (TECHNICAL_DESIGN §8.1): at least 8 characters, at most 72 bytes (BCrypt's limit),
 * and not on the common-passwords list. Null is valid; pair with @NotBlank.
 */
@Documented
@Constraint(validatedBy = StrongPassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

  String message() default "is not strong enough";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<StrongPassword, String> {

    static final int MIN_LENGTH = 8;
    static final int MAX_BYTES = 72;

    private static final Set<String> COMMON = load("common-passwords.txt");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
        return true;
      }
      String problem = null;
      if (value.codePointCount(0, value.length()) < MIN_LENGTH) {
        problem = "must be at least " + MIN_LENGTH + " characters";
      } else if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
        problem = "must be at most " + MAX_BYTES + " bytes";
      } else if (COMMON.contains(value.toLowerCase(Locale.ROOT))) {
        problem = "is too common; choose a less predictable password";
      }
      if (problem == null) {
        return true;
      }
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate(problem).addConstraintViolation();
      return false;
    }

    private static Set<String> load(String resource) {
      var stream = StrongPassword.class.getResourceAsStream(resource);
      if (stream == null) {
        throw new IllegalStateException("Missing " + resource);
      }
      try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        return reader
            .lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(line -> line.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
