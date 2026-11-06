package com.appointmentprep.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/auth/forgot-password}. */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {}
