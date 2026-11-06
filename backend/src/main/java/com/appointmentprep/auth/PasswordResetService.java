package com.appointmentprep.auth;

import com.appointmentprep.common.DomainException;
import com.appointmentprep.common.ErrorCode;
import com.appointmentprep.common.IdGenerator;
import com.appointmentprep.provider.Provider;
import com.appointmentprep.provider.ProviderRepository;
import com.appointmentprep.user.Role;
import com.appointmentprep.user.User;
import com.appointmentprep.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

/** Forgot / reset password (FR-3; TECHNICAL_DESIGN §8.1). */
@Service
public class PasswordResetService {

  static final Duration TTL = Duration.ofHours(1);

  private final UserRepository users;
  private final UserTokenRepository tokens;
  private final RefreshTokenRepository refreshTokens;
  private final ProviderRepository providers;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher events;
  private final IdGenerator ids;
  private final Clock clock;

  public PasswordResetService(
      UserRepository users,
      UserTokenRepository tokens,
      RefreshTokenRepository refreshTokens,
      ProviderRepository providers,
      PasswordEncoder passwordEncoder,
      ApplicationEventPublisher events,
      IdGenerator ids,
      Clock clock) {
    this.users = users;
    this.tokens = tokens;
    this.refreshTokens = refreshTokens;
    this.providers = providers;
    this.passwordEncoder = passwordEncoder;
    this.events = events;
    this.ids = ids;
    this.clock = clock;
  }

  /**
   * Emails a reset link if the account exists, is active and has a password (invited providers
   * accept their invite instead). The caller can't tell which happened (no account enumeration). A
   * new link retires any earlier unused one.
   */
  @Transactional
  public void requestReset(String email) {
    users
        .findByEmail(email.trim())
        .filter(User::isActive)
        .filter(user -> user.getPasswordHash() != null)
        .ifPresent(this::issueLink);
  }

  private void issueLink(User user) {
    // The outbox listener only runs inside a transaction; without one the email would be dropped.
    Assert.state(
        TransactionSynchronizationManager.isActualTransactionActive(),
        "A reset link must be issued inside a transaction");
    Instant now = clock.instant();
    tokens.retireUnused(user.getId(), UserToken.Type.PASSWORD_RESET, now);
    String raw = OpaqueTokens.generate();
    Instant expiresAt = now.plus(TTL);
    tokens.save(
        UserToken.issue(
            ids.next(),
            user.getId(),
            UserToken.Type.PASSWORD_RESET,
            OpaqueTokens.sha256(raw),
            expiresAt));
    // NotificationListener writes the outbox row before this transaction commits.
    events.publishEvent(
        new PasswordResetRequested(
            user.getEmail(), user.getFullName(), emailTimezone(user), raw, expiresAt));
  }

  /**
   * Sets a new password with a valid, unused reset token, then signs the user out everywhere by
   * revoking their refresh tokens.
   */
  @Transactional
  public void resetPassword(String rawToken, String newPassword) {
    Instant now = clock.instant();
    UserToken token =
        tokens
            .findByTokenHash(OpaqueTokens.sha256(rawToken))
            .filter(t -> t.getType() == UserToken.Type.PASSWORD_RESET)
            .filter(t -> t.isUsableAt(now))
            .orElseThrow(PasswordResetService::invalidLink);
    User user =
        users
            .findById(token.getUserId())
            .filter(User::isActive)
            .orElseThrow(PasswordResetService::invalidLink);

    user.changePassword(passwordEncoder.encode(newPassword), now);
    token.markUsed(now);
    refreshTokens.revokeAllForUser(user.getId(), now);
  }

  /** §7.5: emails use the recipient's zone; for providers that's providers.timezone. */
  private String emailTimezone(User user) {
    if (user.getRole() == Role.PROVIDER) {
      return providers
          .findByUserId(user.getId())
          .map(Provider::getTimezone)
          .orElse(user.getTimezone());
    }
    return user.getTimezone();
  }

  private static DomainException invalidLink() {
    return new DomainException(
        ErrorCode.INVALID_TOKEN, "This link is invalid or has expired. Request a new one.");
  }
}
