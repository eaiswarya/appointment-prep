package com.appointmentprep.auth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code POST /api/auth/refresh}. The rotated refresh token travels only in its cookie. */
public record RefreshResponse(@Schema(requiredMode = REQUIRED) String accessToken) {

  /** Never print the access token, e.g. if a response ends up in a log line. */
  @Override
  public String toString() {
    return "RefreshResponse[accessToken=***]";
  }
}
