package com.appointmentprep.auth;

import com.appointmentprep.common.IdGenerator;
import com.appointmentprep.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invite tokens (TECHNICAL_DESIGN §8.1): single use, stored hashed, valid 72 hours. The invite
 * email goes to the outbox in the caller's transaction. Accepting the invite is #28.
 */
@Service
public class InviteService {

  static final Duration TTL = Duration.ofHours(72);

  private final UserTokenRepository tokens;
  private final ApplicationEventPublisher events;
  private final IdGenerator ids;
  private final Clock clock;

  public InviteService(
      UserTokenRepository tokens, ApplicationEventPublisher events, IdGenerator ids, Clock clock) {
    this.tokens = tokens;
    this.events = events;
    this.ids = ids;
    this.clock = clock;
  }

  /**
   * Issues an invite for a user without a password. MANDATORY: it must join the caller's
   * transaction, or the outbox listener (BEFORE_COMMIT) would never run and the email would be
   * lost.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void sendInvite(User user, String timezone) {
    Instant now = clock.instant();
    tokens.retireUnused(user.getId(), UserToken.Type.INVITE, now);
    String raw = OpaqueTokens.generate();
    Instant expiresAt = now.plus(TTL);
    tokens.save(
        UserToken.issue(
            ids.next(), user.getId(), UserToken.Type.INVITE, OpaqueTokens.sha256(raw), expiresAt));
    events.publishEvent(
        new InviteIssued(user.getEmail(), user.getFullName(), timezone, raw, expiresAt));
  }
}
