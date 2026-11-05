package com.appointmentprep.auth;

import com.appointmentprep.common.DomainException;
import com.appointmentprep.common.ErrorCode;
import com.appointmentprep.common.IdGenerator;
import com.appointmentprep.user.User;
import com.appointmentprep.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque 256-bit refresh tokens, stored as SHA-256 hashes, rotated on every use, with reuse
 * detection (TECHNICAL_DESIGN §8.1).
 */
@Service
public class RefreshTokenService {

  static final Duration TTL = Duration.ofDays(7);

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

  /** A successful rotation: a new access token and the new raw refresh token for the cookie. */
  public record Rotation(String accessToken, String refreshToken) {

    /** Never print the tokens, e.g. if a result ends up in a log line. */
    @Override
    public String toString() {
      return "Rotation[accessToken=***, refreshToken=***]";
    }
  }

  private final RefreshTokenRepository tokens;
  private final UserRepository users;
  private final AccessTokenService accessTokens;
  private final IdGenerator ids;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokenService(
      RefreshTokenRepository tokens,
      UserRepository users,
      AccessTokenService accessTokens,
      IdGenerator ids,
      Clock clock) {
    this.tokens = tokens;
    this.users = users;
    this.accessTokens = accessTokens;
    this.ids = ids;
    this.clock = clock;
  }

  /** Stores a new token for the user and returns its raw value (sent once, in the cookie). */
  @Transactional
  public String issue(UUID userId) {
    return store(userId, clock.instant()).raw();
  }

  /**
   * Swaps a valid refresh token for a new one plus a new access token. A token that was already
   * revoked means it leaked: every token of that user is revoked. Committing that revocation is the
   * point, so the 401 thrown afterwards must not roll it back.
   */
  @Transactional(noRollbackFor = DomainException.class)
  public Rotation rotate(String raw) {
    if (raw == null || raw.isBlank()) {
      throw rejected();
    }
    RefreshToken token =
        tokens.findByTokenHash(sha256(raw)).orElseThrow(RefreshTokenService::rejected);
    Instant now = clock.instant();
    if (token.isRevoked()) {
      int revoked = tokens.revokeAllForUser(token.getUserId(), now);
      log.warn(
          "Refresh token reuse for user {}: revoked {} active token(s)",
          token.getUserId(),
          revoked);
      throw rejected();
    }
    if (token.isExpiredAt(now)) {
      throw rejected();
    }
    User user =
        users
            .findById(token.getUserId())
            .filter(User::isActive)
            .orElseThrow(RefreshTokenService::rejected);

    Stored next = store(user.getId(), now);
    token.revoke(now, next.id());
    return new Rotation(accessTokens.issue(user), next.raw());
  }

  /** Logout: revokes the token if it is still active. Unknown or missing tokens are ignored. */
  @Transactional
  public void revoke(String raw) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    tokens
        .findByTokenHash(sha256(raw))
        .filter(token -> !token.isRevoked())
        .ifPresent(token -> token.revoke(clock.instant(), null));
  }

  private record Stored(UUID id, String raw) {}

  private Stored store(UUID userId, Instant now) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    UUID id = ids.next();
    tokens.save(RefreshToken.issue(id, userId, sha256(raw), now.plus(TTL)));
    return new Stored(id, raw);
  }

  private static DomainException rejected() {
    return new DomainException(ErrorCode.UNAUTHENTICATED, "Your session has ended. Please log in.");
  }

  static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
