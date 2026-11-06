package com.appointmentprep.provider;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
