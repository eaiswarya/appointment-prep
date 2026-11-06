package com.appointmentprep.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  private static final RateLimiter.Limit LIMIT =
      new RateLimiter.Limit("test", 2, Duration.ofMinutes(1));

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-03T04:30:00Z"));

  @Test
  void aBucketIsForgottenOnceIdleForAWholePeriodButKeptWhileLimited() {
    RateLimiter limiter = new RateLimiter(clock, true);
    for (int i = 0; i < 1_000; i++) {
      limiter.consume(LIMIT, "idle-" + i);
    }
    limiter.consume(LIMIT, "busy");
    limiter.consume(LIMIT, "busy");
    clock.advance(Duration.ofSeconds(59));

    assertThatThrownBy(() -> limiter.consume(LIMIT, "busy"))
        .isInstanceOf(RateLimitedException.class);
    assertThat(limiter.bucketCount()).isEqualTo(1_001);

    clock.advance(Duration.ofMinutes(1));
    assertThat(limiter.bucketCount()).isZero();
  }

  @Test
  void forgettingAnIdleBucketLosesNothingBecauseItWouldBeFullAgain() {
    RateLimiter limiter = new RateLimiter(clock, true);
    limiter.consume(LIMIT, "ip");
    limiter.consume(LIMIT, "ip");
    clock.advance(Duration.ofMinutes(1));
    limiter.bucketCount(); // evicts it

    limiter.consume(LIMIT, "ip");
    limiter.consume(LIMIT, "ip");
    assertThatThrownBy(() -> limiter.consume(LIMIT, "ip")).isInstanceOf(RateLimitedException.class);
  }

  @Test
  void retryAfterIsRoundedUpToWholeSeconds() {
    RateLimiter limiter = new RateLimiter(clock, true);
    limiter.consume(LIMIT, "ip");
    limiter.consume(LIMIT, "ip");
    clock.advance(Duration.ofMillis(59_500));

    assertThatThrownBy(() -> limiter.consume(LIMIT, "ip"))
        .isInstanceOfSatisfying(
            RateLimitedException.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(1));
  }

  @Test
  void disabledLimiterAllowsEverything() {
    RateLimiter limiter = new RateLimiter(clock, false);

    assertThatCode(
            () -> {
              for (int i = 0; i < 10; i++) {
                limiter.consume(LIMIT, "ip");
              }
            })
        .doesNotThrowAnyException();
  }
}
