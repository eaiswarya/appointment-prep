package com.appointmentprep.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** PUT /provider/availability: the full set of weekly rules (full replace). */
public record UpdateAvailabilityRequest(
    @NotNull @Size(max = 100) List<@NotNull @Valid AvailabilityRuleDto> rules) {}
