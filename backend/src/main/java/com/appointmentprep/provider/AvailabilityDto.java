package com.appointmentprep.provider;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** GET/PUT /provider/availability: the weekly rules and the zone they are in. */
public record AvailabilityDto(
    @Schema(requiredMode = REQUIRED, description = "The provider's IANA zone; rule times are in it")
        String timezone,
    @Schema(requiredMode = REQUIRED, description = "By day, then start time")
        List<AvailabilityRuleDto> rules) {}
