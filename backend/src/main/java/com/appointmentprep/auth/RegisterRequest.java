package com.appointmentprep.auth;

import com.appointmentprep.common.ValidTimeZone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/register}. The timezone comes from the browser (TECHNICAL_DESIGN §10.3).
 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @StrongPassword String password,
    @NotBlank @Size(max = 120) String fullName,
    @NotBlank @ValidTimeZone String timezone) {}
