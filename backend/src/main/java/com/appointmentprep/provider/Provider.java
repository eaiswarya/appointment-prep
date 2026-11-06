package com.appointmentprep.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * A provider's profile (table {@code providers}), one per PROVIDER user. Its time zone drives slot
 * expansion (TECHNICAL_DESIGN §6.1). Never deleted; deactivated with {@code active}.
 */
@Entity
@Table(name = "providers")
public class Provider implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "display_name", length = 120, nullable = false)
  private String displayName;

  @Column(columnDefinition = "text")
  private String bio;

  /** IANA zone, e.g. Asia/Kolkata. */
  @Column(length = 64, nullable = false)
  private String timezone;

  @Column(name = "buffer_minutes", nullable = false)
  private int bufferMinutes;

  @Column(nullable = false)
  private boolean active;

  @Transient private boolean isNew;

  protected Provider() {}

  static Provider create(UUID id, UUID userId, String displayName, String timezone) {
    Provider provider = new Provider();
    provider.id = id;
    provider.userId = userId;
    provider.displayName = displayName;
    provider.timezone = timezone;
    provider.bufferMinutes = 0;
    provider.active = true;
    provider.isNew = true;
    return provider;
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

  public String getDisplayName() {
    return displayName;
  }

  public String getTimezone() {
    return timezone;
  }

  public boolean isActive() {
    return active;
  }
}
