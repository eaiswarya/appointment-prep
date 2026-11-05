package com.appointmentprep.auth;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/auth/login}. No strength rules here; that's for setting a password. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {

  /** Never print the password, e.g. if a request ends up in a log line. */
  @Override
  public String toString() {
    return "LoginRequest[email=%s, password=***]".formatted(email);
  }
}
