package com.appointmentprep.admin;

import com.appointmentprep.common.PageResponse;
import com.appointmentprep.provider.AdminProviderDto;
import com.appointmentprep.provider.InviteProviderRequest;
import com.appointmentprep.provider.ProviderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin: providers (A1; TECHNICAL_DESIGN §9.1). /admin/** is ADMIN-only in SecurityConfig. Creating
 * a provider sends an invite.
 */
@RestController
@RequestMapping("/admin/providers")
public class AdminProvidersController {

  private final ProviderService providers;

  public AdminProvidersController(ProviderService providers) {
    this.providers = providers;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AdminProviderDto inviteProvider(@Valid @RequestBody InviteProviderRequest request) {
    return providers.invite(request);
  }

  @GetMapping
  public PageResponse<AdminProviderDto> listProviders(
      // Capped so page * size stays within the database offset (an int): no 500 on huge pages.
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return providers.list(page, size);
  }
}
