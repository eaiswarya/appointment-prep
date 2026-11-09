package com.appointmentprep.catalog;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /admin/services/{id}. Every field is optional: {@code null} leaves it unchanged. An empty
 * description clears it.
 */
public record UpdateServiceRequest(
    @Size(max = 120) @Pattern(regexp = ServiceNames.PATTERN, message = ServiceNames.MESSAGE)
        String name,
    @Size(max = 2000) String description,
    Integer durationMinutes,
    Boolean active) {}
