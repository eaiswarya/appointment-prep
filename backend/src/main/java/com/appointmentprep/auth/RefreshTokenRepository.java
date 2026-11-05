package com.appointmentprep.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /** Locks the row, so two concurrent refreshes with one token can't both rotate it. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "UPDATE RefreshToken t SET t.revokedAt = :now"
          + " WHERE t.userId = :userId AND t.revokedAt IS NULL")
  int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
