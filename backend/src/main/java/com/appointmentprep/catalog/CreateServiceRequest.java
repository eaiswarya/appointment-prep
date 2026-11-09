package com.appointmentprep.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /admin/services. The duration's range and 5-minute grid are checked by {@link
 * CatalogService}, so they fail with {@code INVALID_DURATION} rather than a field error.
 */
public record CreateServiceRequest(
    @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters")
        String name,
    @Size(max = 2000) String description,
    @NotNull Integer durationMinutes) {}
