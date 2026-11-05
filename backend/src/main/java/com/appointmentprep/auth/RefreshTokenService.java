package com.appointmentprep.auth;

import com.appointmentprep.common.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opaque 256-bit refresh tokens, stored as SHA-256 hashes (TECHNICAL_DESIGN §8.1). */
@Service
public class RefreshTokenService {

  static final Duration TTL = Duration.ofDays(7);

  private final RefreshTokenRepository tokens;
  private final IdGenerator ids;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokenService(RefreshTokenRepository tokens, IdGenerator ids, Clock clock) {
    this.tokens = tokens;
    this.ids = ids;
    this.clock = clock;
  }

  /** Stores a new token for the user and returns its raw value (sent once, in the cookie). */
  @Transactional
  public String issue(UUID userId) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    tokens.save(RefreshToken.issue(ids.next(), userId, sha256(raw), clock.instant().plus(TTL)));
    return raw;
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
