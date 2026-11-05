package com.appointmentprep.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A refresh token (table {@code refresh_tokens}). Only the SHA-256 of the opaque value is stored;
 * rotation sets {@code revoked_at} and {@code replaced_by} (§8.1, #22).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "token_hash", length = 64, nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "replaced_by")
  private UUID replacedBy;

  @Transient private boolean isNew;

  protected RefreshToken() {}

  static RefreshToken issue(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
    RefreshToken token = new RefreshToken();
    token.id = id;
    token.userId = userId;
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

  public UUID getUserId() {
    return userId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
