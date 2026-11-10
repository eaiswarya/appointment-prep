package com.appointmentprep.provider;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** The provider's own profile (GET/PATCH /provider/me; TECHNICAL_DESIGN §9.1). */
public record ProviderProfileDto(
    @Schema(requiredMode = REQUIRED) String displayName,
    String bio,
    @Schema(requiredMode = REQUIRED, description = "IANA zone; weekly hours are in this zone")
        String timezone,
    @Schema(requiredMode = REQUIRED) int bufferMinutes) {

  static ProviderProfileDto of(Provider provider) {
    return new ProviderProfileDto(
        provider.getDisplayName(),
        provider.getBio(),
        provider.getTimezone(),
        provider.getBufferMinutes());
  }
}
