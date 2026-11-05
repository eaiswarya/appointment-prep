package com.appointmentprep.auth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.appointmentprep.user.UserDto;
import io.swagger.v3.oas.annotations.media.Schema;

/** The access token goes in the body; the refresh token only ever travels in its cookie. */
public record LoginResponse(
    @Schema(requiredMode = REQUIRED) String accessToken,
    @Schema(requiredMode = REQUIRED) UserDto user) {

  /** Never print the access token, e.g. if a response ends up in a log line. */
  @Override
  public String toString() {
    return "LoginResponse[accessToken=***, user=" + user.id() + "]";
  }
}
