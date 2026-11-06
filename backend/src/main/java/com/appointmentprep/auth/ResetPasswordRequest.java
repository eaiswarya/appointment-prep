package com.appointmentprep.auth;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/auth/reset-password}: the token from the emailed link and the new password. */
public record ResetPasswordRequest(
    @NotBlank String token, @NotBlank @StrongPassword String newPassword) {

  /** Never print the token or the password, e.g. if a request ends up in a log line. */
  @Override
  public String toString() {
    return "ResetPasswordRequest[token=***, newPassword=***]";
  }
}
