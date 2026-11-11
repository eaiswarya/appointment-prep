package com.appointmentprep.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * A recurring weekly window when a provider works (table {@code availability_rules}; PRODUCT §3).
 * Times are local to the provider's zone and never converted to UTC when stored: slot expansion
 * interprets them in that zone, which is where DST is handled (TECHNICAL_DESIGN §6.1).
 */
@Entity
@Table(name = "availability_rules")
public class AvailabilityRule implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "provider_id", nullable = false)
  private UUID providerId;

  /** ISO day of week, 1 = Monday (smallint in the schema). */
  @Column(name = "day_of_week", nullable = false)
  private short dayOfWeek;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  /** Exclusive. */
  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Transient private boolean isNew;

  protected AvailabilityRule() {}

  static AvailabilityRule create(
      UUID id, UUID providerId, DayOfWeek day, LocalTime startTime, LocalTime endTime) {
    AvailabilityRule rule = new AvailabilityRule();
    rule.id = id;
    rule.providerId = providerId;
    rule.dayOfWeek = (short) day.getValue();
    rule.startTime = startTime;
    rule.endTime = endTime;
    rule.isNew = true;
    return rule;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    isNew = false;
  }

  public DayOfWeek getDayOfWeek() {
    return DayOfWeek.of(dayOfWeek);
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }
}
