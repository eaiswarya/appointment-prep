package com.appointmentprep.provider;

import com.appointmentprep.catalog.CatalogItem;
import com.appointmentprep.catalog.CatalogItemRepository;
import com.appointmentprep.catalog.ServiceDto;
import com.appointmentprep.common.FieldValidationException;
import com.appointmentprep.common.NotFoundException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which catalog services a provider offers (P3; table {@code provider_services}). Only active
 * services are shown or chosen; links to deactivated services are kept untouched, so reactivating a
 * service restores it for its providers.
 */
@Service
public class ProviderOfferingService {

  private final ProviderRepository providers;
  private final CatalogItemRepository services;

  public ProviderOfferingService(ProviderRepository providers, CatalogItemRepository services) {
    this.providers = providers;
    this.services = services;
  }

  @Transactional(readOnly = true)
  public ProviderServicesDto get(UUID userId) {
    Provider provider =
        providers.findByUserId(userId).orElseThrow(ProviderOfferingService::notFound);
    return view(provider);
  }

  /**
   * Replaces the provider's set of active services. Every id must be an active service; otherwise
   * nothing changes. The provider row is locked so concurrent saves can't interleave.
   */
  @Transactional
  public ProviderServicesDto replace(UUID userId, UpdateProviderServicesRequest request) {
    Provider provider =
        providers
            .findByUserIdForUpdate(userId)
            .filter(Provider::isActive)
            .orElseThrow(ProviderOfferingService::notFound);
    Set<UUID> ids = new LinkedHashSet<>(request.serviceIds());
    List<CatalogItem> chosen = services.findAllById(ids);
    if (chosen.size() != ids.size() || !chosen.stream().allMatch(CatalogItem::isActive)) {
      throw new FieldValidationException(
          "serviceIds", "must only contain services that exist and are active");
    }
    providers.deleteActiveOfferings(provider.getId());
    ids.forEach(id -> providers.addOffering(provider.getId(), id));
    return view(provider);
  }

  private ProviderServicesDto view(Provider provider) {
    List<UUID> offered =
        services.findActiveOfferedBy(provider.getId()).stream().map(CatalogItem::getId).toList();
    List<ServiceDto> available = services.findActive().stream().map(ServiceDto::of).toList();
    return new ProviderServicesDto(offered, available);
  }

  private static NotFoundException notFound() {
    return new NotFoundException("Provider profile not found.");
  }
}
