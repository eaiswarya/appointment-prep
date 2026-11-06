package com.appointmentprep.auth;

import java.time.Instant;

/**
 * Published inside the reset-request transaction (TECHNICAL_DESIGN §7.1). Carries the raw token
 * once, for the emailed link; never log it.
 */
public record PasswordResetRequested(
    String email, String fullName, String timezone, String rawToken, Instant expiresAt) {

  @Override
  public String toString() {
    return "PasswordResetRequested[email=%s, rawToken=***, expiresAt=%s]"
        .formatted(email, expiresAt);
  }
}
