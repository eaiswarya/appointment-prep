package com.appointmentprep.notification;

import com.appointmentprep.auth.PasswordResetRequested;
import com.appointmentprep.common.IdGenerator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns domain events into {@code email_outbox} rows inside the same transaction (BEFORE_COMMIT),
 * so an email row exists exactly when the business change commits (TECHNICAL_DESIGN §7.1). It never
 * sends anything; the dispatcher does that in M5.
 */
@Component
public class NotificationListener {

  private final EmailOutboxRepository outbox;
  private final IdGenerator ids;
  private final Clock clock;
  private final String baseUrl;

  public NotificationListener(
      EmailOutboxRepository outbox,
      IdGenerator ids,
      Clock clock,
      @Value("${app.base-url}") String baseUrl) {
    this.outbox = outbox;
    this.ids = ids;
    this.clock = clock;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(PasswordResetRequested event) {
    String link =
        baseUrl
            + "/reset-password?token="
            + URLEncoder.encode(event.rawToken(), StandardCharsets.UTF_8);
    Map<String, Object> payload =
        Map.of(
            "name", event.fullName(),
            "resetLink", link,
            "expiresAt", event.expiresAt().toString(),
            "timezone", event.timezone());
    outbox.save(
        EmailOutbox.pending(
            ids.next(), EmailTemplate.PASSWORD_RESET, event.email(), payload, clock.instant()));
  }
}
