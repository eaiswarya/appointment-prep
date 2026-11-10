package com.appointmentprep.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

  /** Admin list: every service, active or not, by name. */
  @Query(
      value = "SELECT s FROM CatalogItem s ORDER BY lower(s.name), s.id",
      countQuery = "SELECT count(s) FROM CatalogItem s")
  Page<CatalogItem> findAllByName(Pageable pageable);
}
