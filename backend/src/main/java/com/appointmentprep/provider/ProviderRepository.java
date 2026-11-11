package com.appointmentprep.provider;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {

  /** Admin list: provider plus its user, ordered by display name. Pending = no password yet. */
  @Query(
      value =
          "SELECT new com.appointmentprep.provider.AdminProviderDto("
              + " p.id, u.email, u.fullName, p.displayName, p.timezone, p.active,"
              + " CASE WHEN u.passwordHash IS NULL THEN true ELSE false END)"
              + " FROM Provider p, User u WHERE u.id = p.userId"
              + " ORDER BY lower(p.displayName), p.id",
      countQuery = "SELECT count(p) FROM Provider p")
  Page<AdminProviderDto> findAllForAdmin(Pageable pageable);

  Optional<Provider> findByUserId(UUID userId);

  /** Locks the provider's row, so two edits of the same provider's data run one after the other. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Provider p WHERE p.userId = :userId")
  Optional<Provider> findByUserIdForUpdate(@Param("userId") UUID userId);

  /**
   * Drops the provider's links to <em>active</em> services. Links to deactivated services stay, so
   * reactivating a service restores it for its providers.
   */
  @Modifying
  @Query(
      value =
          "DELETE FROM provider_services ps USING services s"
              + " WHERE ps.service_id = s.id AND s.active AND ps.provider_id = :providerId",
      nativeQuery = true)
  void deleteActiveOfferings(@Param("providerId") UUID providerId);

  @Modifying
  @Query(
      value =
          "INSERT INTO provider_services (provider_id, service_id) VALUES (:providerId, :serviceId)"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void addOffering(@Param("providerId") UUID providerId, @Param("serviceId") UUID serviceId);
}
