package com.appointmentprep.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

  @PostMapping("/validate")
  String validate(@Valid @RequestBody ProbeRequest request) {
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
