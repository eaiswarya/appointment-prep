package com.appointmentprep.notification;

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
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * An email waiting to be sent (table {@code email_outbox}, TECHNICAL_DESIGN §7.1). Rows are written
 * in the same transaction as the business change; the dispatcher (M5) sends them later.
 */
@Entity
@Table(name = "email_outbox")
public class EmailOutbox implements Persistable<UUID> {

  public enum Status {
    PENDING,
    SENT,
    FAILED
  }

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(length = 50, nullable = false)
  private EmailTemplate template;

  @Column(length = 254, nullable = false)
  private String recipient;

  /** Template variables, snapshotted when the event happened. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> payload;

  @Column(name = "booking_id")
  private UUID bookingId;

  @Enumerated(EnumType.STRING)
  @Column(length = 10, nullable = false)
  private Status status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Transient private boolean isNew;

  protected EmailOutbox() {}

  static EmailOutbox pending(
      UUID id, EmailTemplate template, String recipient, Map<String, Object> payload, Instant now) {
    EmailOutbox email = new EmailOutbox();
    email.id = id;
    email.template = template;
    email.recipient = recipient;
    email.payload = payload;
    email.status = Status.PENDING;
    email.attempts = 0;
    email.nextAttemptAt = now;
    email.createdAt = now;
    email.isNew = true;
    return email;
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
}
