package com.appointmentprep.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * A person who can log in (table {@code users}). Never deleted; deactivated with {@code active}.
 */
// Only changed columns are written, so concurrent edits of different fields (a profile save during
// a password reset) can't overwrite each other with stale values.
@DynamicUpdate
@Entity
@Table(name = "users")
public class User implements Persistable<UUID> {

  @Id private UUID id;

  @Column(columnDefinition = "citext", nullable = false)
  private String email;

  /** BCrypt hash; null until an invited provider sets a password. */
  @Column(name = "password_hash", length = 100)
  private String passwordHash;

  @Column(name = "full_name", length = 120, nullable = false)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private Role role;

  /** IANA zone, used to format emails (TECHNICAL_DESIGN §7.5). */
  @Column(length = 64, nullable = false)
  private String timezone;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // IDs are assigned by the application, so tell Spring Data when a row is new (insert, not merge).
  @Transient private boolean isNew;

  protected User() {}

  public static User newCustomer(
      UUID id, String email, String passwordHash, String fullName, String timezone, Instant now) {
    return create(id, email, passwordHash, fullName, Role.CUSTOMER, timezone, now);
  }

  /** The bootstrap admin (TECHNICAL_DESIGN §12.3); admins can't register themselves. */
  public static User newAdmin(
      UUID id, String email, String passwordHash, String fullName, String timezone, Instant now) {
    return create(id, email, passwordHash, fullName, Role.ADMIN, timezone, now);
  }

  private static User create(
      UUID id,
      String email,
      String passwordHash,
      String fullName,
      Role role,
      String timezone,
      Instant now) {
    User user = new User();
    user.id = id;
    user.email = email;
    user.passwordHash = passwordHash;
    user.fullName = fullName;
    user.role = role;
    user.timezone = timezone;
    user.active = true;
    user.createdAt = now;
    user.updatedAt = now;
    user.isNew = true;
    return user;
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

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFullName() {
    return fullName;
  }

  public Role getRole() {
    return role;
  }

  public String getTimezone() {
    return timezone;
  }

  /**
   * Profile edit (PATCH /me): null means "leave unchanged". {@code updated_at} moves only when a
   * value actually changes, so an empty PATCH doesn't write.
   */
  public void updateProfile(String fullName, String timezone, Instant now) {
    boolean changed = false;
    if (fullName != null && !fullName.strip().equals(this.fullName)) {
      this.fullName = fullName.strip(); // strip(), not trim(): also Unicode spaces such as NBSP
      changed = true;
    }
    if (timezone != null && !timezone.equals(this.timezone)) {
      this.timezone = timezone;
      changed = true;
    }
    if (changed) {
      this.updatedAt = now;
    }
  }

  /** Sets a new BCrypt hash (password reset, invite acceptance). */
  public void changePassword(String passwordHash, Instant now) {
    this.passwordHash = passwordHash;
    this.updatedAt = now;
  }

  public boolean isActive() {
    return active;
  }
}
