package com.appointmentprep.auth;

import com.appointmentprep.common.DomainException;
import com.appointmentprep.common.ErrorCode;
import com.appointmentprep.common.IdGenerator;
import com.appointmentprep.user.User;
import com.appointmentprep.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invite tokens (TECHNICAL_DESIGN §8.1): single use, stored hashed, valid 72 hours. The invite
 * email goes to the outbox in the caller's transaction; the provider accepts by setting a password.
 */
@Service
public class InviteService {

  static final Duration TTL = Duration.ofHours(72);

  private final UserTokenRepository tokens;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher events;
  private final IdGenerator ids;
  private final Clock clock;

  public InviteService(
      UserTokenRepository tokens,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      ApplicationEventPublisher events,
      IdGenerator ids,
      Clock clock) {
    this.tokens = tokens;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
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

  /**
   * Sets the invited user's first password with a valid, unused INVITE token (FR-1). The token row
   * is locked, so a link can be redeemed only once even when used twice at the same moment.
   */
  @Transactional
  public void acceptInvite(String rawToken, String password) {
    Instant now = clock.instant();
    UserToken token =
        tokens
            .findByTokenHash(OpaqueTokens.sha256(rawToken))
            .filter(t -> t.getType() == UserToken.Type.INVITE)
            .filter(t -> t.isUsableAt(now))
            .orElseThrow(InviteService::invalidInvite);
    User user =
        users
            .findById(token.getUserId())
            .filter(User::isActive)
            .orElseThrow(InviteService::invalidInvite);

    user.changePassword(passwordEncoder.encode(password), now);
    token.markUsed(now);
  }

  private static DomainException invalidInvite() {
    return new DomainException(
        ErrorCode.INVALID_TOKEN,
        "This invite link is invalid or has expired. Ask your admin for a new one.");
  }
}
