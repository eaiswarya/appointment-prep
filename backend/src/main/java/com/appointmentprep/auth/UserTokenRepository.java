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

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

  /** Locks the row, so the same link can't be redeemed twice concurrently. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<UserToken> findByTokenHash(String tokenHash);

  /** Marks the user's unused tokens of a type as used, e.g. when a newer link is sent. */
  @Modifying
  @Query(
      "UPDATE UserToken t SET t.usedAt = :now"
          + " WHERE t.userId = :userId AND t.type = :type AND t.usedAt IS NULL")
  int retireUnused(
      @Param("userId") UUID userId, @Param("type") UserToken.Type type, @Param("now") Instant now);
}
