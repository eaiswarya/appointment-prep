package com.appointmentprep.user;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A user as the API returns it. Never includes the password hash. */
public record UserDto(
    // Response fields are always present; mark them required so the generated TS types aren't
    // optional.
    @Schema(requiredMode = REQUIRED) UUID id,
    @Schema(requiredMode = REQUIRED) String email,
    @Schema(requiredMode = REQUIRED) String fullName,
    @Schema(requiredMode = REQUIRED) Role role,
    @Schema(requiredMode = REQUIRED) String timezone) {

  public static UserDto from(User user) {
    return new UserDto(
        user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getTimezone());
  }
}
