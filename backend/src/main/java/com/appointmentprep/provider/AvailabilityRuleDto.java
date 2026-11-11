package com.appointmentprep.provider;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * One weekly window, in the provider's local time (TECHNICAL_DESIGN §9.1). The 5-minute grid and
 * start-before-end are checked by {@link AvailabilityService}, together with overlaps.
 */
public record AvailabilityRuleDto(
    @Schema(requiredMode = REQUIRED, description = "ISO day of week, 1 = Monday … 7 = Sunday")
        @NotNull
        @Min(1)
        @Max(7)
        Integer dayOfWeek,
    @Schema(
            requiredMode = REQUIRED,
            type = "string",
            pattern = "^\\d{2}:\\d{2}$",
            example = "09:00")
        @JsonFormat(pattern = "HH:mm")
        @NotNull
        LocalTime startTime,
    @Schema(
            requiredMode = REQUIRED,
            type = "string",
            pattern = "^\\d{2}:\\d{2}$",
            example = "17:00",
            description = "Exclusive; on the same day as the start")
        @JsonFormat(pattern = "HH:mm")
        @NotNull
        LocalTime endTime) {

  static AvailabilityRuleDto of(AvailabilityRule rule) {
    return new AvailabilityRuleDto(
        rule.getDayOfWeek().getValue(), rule.getStartTime(), rule.getEndTime());
  }
}
