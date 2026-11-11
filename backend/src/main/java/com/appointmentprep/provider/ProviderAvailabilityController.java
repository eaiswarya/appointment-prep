package com.appointmentprep.provider;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling provider's weekly hours (P1; TECHNICAL_DESIGN §9.1). /provider/** is PROVIDER-only in
 * SecurityConfig; the provider is found by the JWT subject.
 */
@RestController
@RequestMapping("/provider/availability")
public class ProviderAvailabilityController {

  private final AvailabilityService availability;

  public ProviderAvailabilityController(AvailabilityService availability) {
    this.availability = availability;
  }

  @GetMapping
  public AvailabilityDto getAvailability(@AuthenticationPrincipal Jwt jwt) {
    return availability.get(UUID.fromString(jwt.getSubject()));
  }

  /** Full replace of the weekly rules. */
  @PutMapping
  public AvailabilityDto replaceAvailability(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateAvailabilityRequest request) {
    return availability.replace(UUID.fromString(jwt.getSubject()), request);
  }
}
