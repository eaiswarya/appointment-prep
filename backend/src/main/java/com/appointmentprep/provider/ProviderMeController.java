package com.appointmentprep.provider;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling provider's own profile (TECHNICAL_DESIGN §9.1). /provider/** is PROVIDER-only in
 * SecurityConfig; the provider is found by the JWT subject, never by an id from the client.
 */
@RestController
@RequestMapping("/provider/me")
public class ProviderMeController {

  private final ProviderService providers;

  public ProviderMeController(ProviderService providers) {
    this.providers = providers;
  }

  @GetMapping
  public ProviderProfileDto getProviderProfile(@AuthenticationPrincipal Jwt jwt) {
    return providers.getProfile(UUID.fromString(jwt.getSubject()));
  }

  /** Partial update of display name, bio, time zone and/or buffer. */
  @PatchMapping
  public ProviderProfileDto updateProviderProfile(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProviderProfileRequest request) {
    return providers.updateProfile(UUID.fromString(jwt.getSubject()), request);
  }
}
