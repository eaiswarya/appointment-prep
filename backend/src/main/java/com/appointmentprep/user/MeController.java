package com.appointmentprep.user;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user (TECHNICAL_DESIGN §9.1 {@code GET /me}). */
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
}
