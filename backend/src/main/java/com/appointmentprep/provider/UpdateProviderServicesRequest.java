package com.appointmentprep.provider;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** PUT /provider/services: the full set of active services offered. Duplicates count once. */
public record UpdateProviderServicesRequest(
    @NotNull @Size(max = 200) List<@NotNull UUID> serviceIds) {}
