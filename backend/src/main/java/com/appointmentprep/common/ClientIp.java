package com.appointmentprep.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The client's IP address, for per-IP rate limits. The backend is reachable only through our Nginx
 * (TECHNICAL_DESIGN §3), which appends the address it saw to {@code X-Forwarded-For}. So only the
 * last entry is trusted; anything before it was sent by the client and may be forged.
 */
public final class ClientIp {

  private ClientIp() {}

  public static String of(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null) {
      String last = forwardedFor.substring(forwardedFor.lastIndexOf(',') + 1).strip();
      if (!last.isEmpty()) {
        return last;
      }
    }
    return request.getRemoteAddr();
  }
}
