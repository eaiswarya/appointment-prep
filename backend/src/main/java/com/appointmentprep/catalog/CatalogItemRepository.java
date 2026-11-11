package com.appointmentprep.catalog;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {

  /** Public catalog: active services only, by name. */
  @Query("SELECT s FROM CatalogItem s WHERE s.active = true ORDER BY lower(s.name), s.id")
  List<CatalogItem> findActive();

  /** The active services a provider offers, by name. */
  @Query(
      value =
          "SELECT s.* FROM services s JOIN provider_services ps ON ps.service_id = s.id"
              + " WHERE ps.provider_id = :providerId AND s.active"
              + " ORDER BY lower(s.name), s.id",
      nativeQuery = true)
  List<CatalogItem> findActiveOfferedBy(@Param("providerId") UUID providerId);

  /**
   * The given services, share-locked (FOR SHARE) until commit, so their {@code active} flag can't
   * change while a provider's offerings are being replaced.
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("SELECT s FROM CatalogItem s WHERE s.id IN :ids")
  List<CatalogItem> findAllByIdForShare(@Param("ids") Collection<UUID> ids);

  /** Share-locks every service the provider is linked to, active or not (see above). */
  @Query(
      value =
          "SELECT s.* FROM services s WHERE s.id IN"
              + " (SELECT service_id FROM provider_services WHERE provider_id = :providerId)"
              + " FOR SHARE",
      nativeQuery = true)
  List<CatalogItem> lockOfferedBy(@Param("providerId") UUID providerId);

  /** Admin list: every service, active or not, by name. */
  @Query(
      value = "SELECT s FROM CatalogItem s ORDER BY lower(s.name), s.id",
      countQuery = "SELECT count(s) FROM CatalogItem s")
  Page<CatalogItem> findAllByName(Pageable pageable);
}
