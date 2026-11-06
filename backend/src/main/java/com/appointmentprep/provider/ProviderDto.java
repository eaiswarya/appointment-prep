package com.appointmentprep.provider;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A provider as the admin API returns it. {@code invitePending}: the invite isn't accepted yet. */
public record ProviderDto(
    @Schema(requiredMode = REQUIRED) UUID id,
    @Schema(requiredMode = REQUIRED) String email,
    @Schema(requiredMode = REQUIRED) String fullName,
    @Schema(requiredMode = REQUIRED) String displayName,
    @Schema(requiredMode = REQUIRED) String timezone,
    @Schema(requiredMode = REQUIRED) boolean active,
    @Schema(requiredMode = REQUIRED) boolean invitePending) {}
