package com.appointmentprep.user;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user (TECHNICAL_DESIGN §9.1 {@code GET /me} · {@code PATCH /me}). */
@RestController
@RequestMapping("/me")
public class MeController {

  private final UserService users;

  public MeController(UserService users) {
    this.users = users;
  }

  @GetMapping
  public UserDto me(@AuthenticationPrincipal Jwt jwt) {
    return users.get(UUID.fromString(jwt.getSubject()));
  }

  /** Partial update of the caller's own name and/or timezone. */
  @PatchMapping
  public UserDto updateMe(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
    return users.updateProfile(UUID.fromString(jwt.getSubject()), request);
  }
}
