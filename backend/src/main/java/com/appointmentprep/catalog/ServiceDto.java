package com.appointmentprep.catalog;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A catalog service as the API shows it (TECHNICAL_DESIGN §9.1). */
public record ServiceDto(
    @Schema(requiredMode = REQUIRED) UUID id,
    @Schema(requiredMode = REQUIRED) String name,
    String description,
    @Schema(requiredMode = REQUIRED) int durationMinutes,
    @Schema(requiredMode = REQUIRED) boolean active) {

  public static ServiceDto of(CatalogItem item) {
    return new ServiceDto(
        item.getId(),
        item.getName(),
        item.getDescription(),
        item.getDurationMinutes(),
        item.isActive());
  }
}
