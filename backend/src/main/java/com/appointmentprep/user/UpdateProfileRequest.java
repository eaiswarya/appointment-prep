package com.appointmentprep.user;

import com.appointmentprep.common.ValidTimeZone;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/me}: a partial update, so a null field is left unchanged. Email and role can't
 * be changed here (unknown JSON fields are ignored).
 */
public record UpdateProfileRequest(
    @Size(max = 120)
        // (?U): Unicode-aware \S, so a name of only NBSP or ideographic spaces counts as blank.
        @Pattern(regexp = "(?U)(?s).*\\S.*", message = "must not be blank")
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters")
        String fullName,
    @ValidTimeZone String timezone) {}
