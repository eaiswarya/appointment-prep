package com.appointmentprep.auth;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * A single-use invite or password-reset token (table {@code user_tokens}). Only the SHA-256 of the
 * emailed value is stored (TECHNICAL_DESIGN §8.1).
 */
@Entity
@Table(name = "user_tokens")
public class UserToken implements Persistable<UUID> {

  public enum Type {
    INVITE,
    PASSWORD_RESET
  }

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private Type type;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "token_hash", length = 64, nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Transient private boolean isNew;

  protected UserToken() {}

  static UserToken issue(UUID id, UUID userId, Type type, String tokenHash, Instant expiresAt) {
    UserToken token = new UserToken();
    token.id = id;
    token.userId = userId;
    token.type = type;
    token.tokenHash = tokenHash;
    token.expiresAt = expiresAt;
    token.isNew = true;
    return token;
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

  UUID getUserId() {
    return userId;
  }

  Type getType() {
    return type;
  }

  /** Usable now: not used yet and not expired. */
  boolean isUsableAt(Instant now) {
    return usedAt == null && now.isBefore(expiresAt);
  }

  void markUsed(Instant now) {
    usedAt = now;
  }
}
