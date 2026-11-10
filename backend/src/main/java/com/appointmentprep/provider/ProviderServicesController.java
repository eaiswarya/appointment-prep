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
 * The services the calling provider offers (P3; TECHNICAL_DESIGN §9.1). /provider/** is
 * PROVIDER-only in SecurityConfig; the provider is found by the JWT subject.
 */
@RestController
@RequestMapping("/provider/services")
public class ProviderServicesController {

  private final ProviderOfferingService offerings;

  public ProviderServicesController(ProviderOfferingService offerings) {
    this.offerings = offerings;
  }

  @GetMapping
  public ProviderServicesDto getProviderServices(@AuthenticationPrincipal Jwt jwt) {
    return offerings.get(UUID.fromString(jwt.getSubject()));
  }

  /** Full replace of the active services offered. */
  @PutMapping
  public ProviderServicesDto replaceProviderServices(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProviderServicesRequest request) {
    return offerings.replace(UUID.fromString(jwt.getSubject()), request);
  }
}
