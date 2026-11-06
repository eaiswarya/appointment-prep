package com.appointmentprep.auth;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/auth/accept-invite}: the token from the emailed link and the first password. */
public record AcceptInviteRequest(
    @NotBlank String token, @NotBlank @StrongPassword String password) {

  /** Never print the token or the password, e.g. if a request ends up in a log line. */
  @Override
  public String toString() {
    return "AcceptInviteRequest[token=***, password=***]";
  }
}
