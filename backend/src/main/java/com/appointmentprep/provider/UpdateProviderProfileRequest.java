package com.appointmentprep.provider;

import com.appointmentprep.common.MultipleOf;
import com.appointmentprep.common.ValidTimeZone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /provider/me. Every field is optional: {@code null} leaves it unchanged. A blank bio clears
 * it. The buffer range mirrors the {@code providers.buffer_minutes} CHECK (§5.2).
 */
public record UpdateProviderProfileRequest(
    @Size(max = 120)
        // (?U): Unicode-aware \S, so a name of only NBSP or ideographic spaces counts as blank.
        @Pattern(regexp = "(?U)(?s).*\\S.*", message = "must not be blank")
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters")
        String displayName,
    @Size(max = 2000) String bio,
    @ValidTimeZone String timezone,
    @Min(0) @Max(120) @MultipleOf(5) Integer bufferMinutes) {}
