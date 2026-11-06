package com.appointmentprep.provider;

import com.appointmentprep.common.ValidTimeZone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /api/admin/providers}. The time zone drives the provider's slots (§6.1). */
public record InviteProviderRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters")
        String fullName,
    @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters")
        String displayName,
    @NotBlank @ValidTimeZone String timezone) {}
