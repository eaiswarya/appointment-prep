package com.appointmentprep.common;

import java.time.Duration;

/** 429 {@code RATE_LIMITED}; the handler sends {@code Retry-After} in whole seconds. */
public class RateLimitedException extends DomainException {

  private final long retryAfterSeconds;

  public RateLimitedException(Duration retryAfter) {
    this(Math.max(1, (retryAfter.toNanos() + 999_999_999L) / 1_000_000_000L));
  }

  private RateLimitedException(long retryAfterSeconds) {
    super(
        ErrorCode.RATE_LIMITED,
        "Too many attempts. Try again in " + retryAfterSeconds + " seconds.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
