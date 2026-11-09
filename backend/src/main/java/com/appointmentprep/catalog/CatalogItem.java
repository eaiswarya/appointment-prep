package com.appointmentprep.catalog;

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
 * A service in the catalog (table {@code services}; PRODUCT §3), e.g. "Initial consultation — 30
 * min". Named CatalogItem so it doesn't clash with Spring's {@code @Service}. Never deleted;
 * deactivated with {@code active}.
 */
@Entity
@Table(name = "services")
public class CatalogItem implements Persistable<UUID> {

  @Id private UUID id;

  @Column(length = 120, nullable = false)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Column(nullable = false)
  private boolean active;

  @Transient private boolean isNew;

  protected CatalogItem() {}

  static CatalogItem create(UUID id, String name, String description, int durationMinutes) {
    CatalogItem item = new CatalogItem();
    item.id = id;
    item.name = name;
    item.description = description;
    item.durationMinutes = durationMinutes;
    item.active = true;
    item.isNew = true;
    return item;
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

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public boolean isActive() {
    return active;
  }

  void rename(String name) {
    this.name = name;
  }

  void describe(String description) {
    this.description = description;
  }

  void changeDuration(int durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  void setActive(boolean active) {
    this.active = active;
  }
}
