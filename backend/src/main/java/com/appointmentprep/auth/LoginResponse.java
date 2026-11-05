package com.appointmentprep.auth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.appointmentprep.user.UserDto;
import io.swagger.v3.oas.annotations.media.Schema;

/** The access token goes in the body; the refresh token only ever travels in its cookie. */
public record LoginResponse(
    @Schema(requiredMode = REQUIRED) String accessToken,
    @Schema(requiredMode = REQUIRED) UserDto user) {}
