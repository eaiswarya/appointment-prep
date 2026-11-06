package com.appointmentprep.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random 256-bit tokens for links and cookies (refresh, invite, password reset). Only their SHA-256
 * is stored, so a database leak doesn't hand out working tokens (TECHNICAL_DESIGN §8.1).
 */
public final class OpaqueTokens {

  private static final SecureRandom RANDOM = new SecureRandom();

  private OpaqueTokens() {}

  /** A new URL-safe token (43 characters). */
  public static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Hex SHA-256, as stored in {@code token_hash} columns (char(64)). */
  public static String sha256(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
