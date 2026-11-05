package com.appointmentprep.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh-token cookie (TECHNICAL_DESIGN §8.1): HttpOnly, Secure, SameSite=Strict, and only
 * sent to /api/auth/* (refresh, logout), never to other API calls.
 */
@Component
public class AuthCookies {

  static final String REFRESH_COOKIE = "refresh_token";

  private final String path;

  public AuthCookies(@Value("${server.servlet.context-path:}") String contextPath) {
    this.path = contextPath + "/auth";
  }

  ResponseCookie refresh(String rawToken) {
    return build(rawToken, RefreshTokenService.TTL);
  }

  /** Same name and path with Max-Age=0, so the browser deletes it. */
  ResponseCookie cleared() {
    return build("", Duration.ZERO);
  }

  private ResponseCookie build(String value, Duration maxAge) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path(path)
        .maxAge(maxAge)
        .build();
  }
}
