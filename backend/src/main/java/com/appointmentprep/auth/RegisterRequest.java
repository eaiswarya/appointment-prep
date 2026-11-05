package com.appointmentprep.auth;

import com.appointmentprep.common.ValidTimeZone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/register}. The timezone comes from the browser (TECHNICAL_DESIGN §10.3).
 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @StrongPassword String password,
    @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters")
        String fullName,
    @NotBlank @ValidTimeZone String timezone) {

  /** Never print the password, e.g. if a request ends up in a log line. */
  @Override
  public String toString() {
    return "RegisterRequest[email=%s, password=***, fullName=%s, timezone=%s]"
        .formatted(email, fullName, timezone);
  }
}
