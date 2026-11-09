package com.appointmentprep.admin;

import com.appointmentprep.catalog.CatalogService;
import com.appointmentprep.catalog.CreateServiceRequest;
import com.appointmentprep.catalog.ServiceDto;
import com.appointmentprep.catalog.UpdateServiceRequest;
import com.appointmentprep.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin: the service catalog (A2; TECHNICAL_DESIGN §9.1). /admin/** is ADMIN-only in
 * SecurityConfig. Services are deactivated, never deleted.
 */
@RestController
@RequestMapping("/admin/services")
public class AdminServicesController {

  private final CatalogService catalog;

  public AdminServicesController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ServiceDto createService(@Valid @RequestBody CreateServiceRequest request) {
    return catalog.create(request);
  }

  @GetMapping
  public PageResponse<ServiceDto> listServices(
      // Capped so page * size stays within the database offset (an int): no 500 on huge pages.
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return catalog.listAll(page, size);
  }

  @PatchMapping("/{id}")
  public ServiceDto updateService(
      @PathVariable UUID id, @Valid @RequestBody UpdateServiceRequest request) {
    return catalog.update(id, request);
  }
}
