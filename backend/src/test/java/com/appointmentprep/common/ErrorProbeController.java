package com.appointmentprep.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints that fail on purpose. Active only under the {@code error-probe} profile, so
 * full-app tests (and the exported OpenAPI spec) never see it.
 */
@Profile("error-probe")
@RestController
@RequestMapping("/probe")
class ErrorProbeController {

  record ProbeRequest(@NotBlank String name, @Size(max = 3) String code) {}

  record TypedRequest(Instant startAt) {}

  private final Validator validator;

  ErrorProbeController(Validator validator) {
    this.validator = validator;
  }

  @PostMapping("/validate")
  String validate(@Valid @RequestBody ProbeRequest request) {
    return "ok";
  }

  /** Constraint on a parameter: Spring's built-in method validation, not @Valid on a body. */
  @GetMapping("/page")
  String page(@RequestParam @Max(100) int size) {
    return "ok";
  }

  @PostMapping("/typed")
  String typed(@RequestBody TypedRequest request) {
    return "ok";
  }

  /** Validation that fails inside a service rather than at the controller boundary. */
  @PostMapping("/service-validate")
  String serviceValidate(@RequestBody ProbeRequest request) {
    Set<ConstraintViolation<ProbeRequest>> violations = validator.validate(request);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
    return "ok";
  }

  @GetMapping("/not-found")
  String notFound() {
    throw new NotFoundException("Booking 42 was not found.");
  }

  @GetMapping("/boom")
  String boom() {
    throw new IllegalStateException("secret internal detail");
  }
}
