package com.appointmentprep.provider;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {

  /** The provider's weekly rules, by day then start time. */
  @Query(
      "SELECT r FROM AvailabilityRule r WHERE r.providerId = :providerId"
          + " ORDER BY r.dayOfWeek, r.startTime")
  List<AvailabilityRule> findForProvider(@Param("providerId") UUID providerId);

  @Modifying(flushAutomatically = true)
  @Query("DELETE FROM AvailabilityRule r WHERE r.providerId = :providerId")
  void deleteForProvider(@Param("providerId") UUID providerId);
}
