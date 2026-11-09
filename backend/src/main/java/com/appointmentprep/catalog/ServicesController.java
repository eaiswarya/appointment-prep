package com.appointmentprep.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public catalog (TECHNICAL_DESIGN §9.1). GET /services is public in SecurityConfig. */
@RestController
@RequestMapping("/services")
public class ServicesController {

  private final CatalogService catalog;

  public ServicesController(CatalogService catalog) {
    this.catalog = catalog;
  }

  /** Active services, sorted by name. */
  @GetMapping
  public List<ServiceDto> listActiveServices() {
    return catalog.listActive();
  }
}
