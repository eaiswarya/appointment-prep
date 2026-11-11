package com.appointmentprep.provider;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.appointmentprep.catalog.ServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** GET/PUT /provider/services: what the provider offers, and the active services to choose from. */
public record ProviderServicesDto(
    @Schema(requiredMode = REQUIRED, description = "Active services this provider offers")
        List<UUID> offeredServiceIds,
    @Schema(requiredMode = REQUIRED, description = "All active services, by name")
        List<ServiceDto> available) {}
