package com.appointmentprep.common;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory rate limits (TECHNICAL_DESIGN §8.3), one Bucket4j bucket per limit and key (e.g. client
 * IP). Each limit allows {@code capacity} requests per fixed {@code period}, counted from the first
 * request. Buckets read the injected {@link Clock}, so tests move time instead of sleeping.
 */
@Component
public class RateLimiter {

  /** A named limit: {@code capacity} requests per {@code period}. */
  public record Limit(String name, int capacity, Duration period) {}

  private record Key(Limit limit, String key) {}

  // Past this many buckets, drop the full (idle) ones so the map can't grow without bound.
  private static final int SWEEP_THRESHOLD = 10_000;

  private final ConcurrentMap<Key, Bucket> buckets = new ConcurrentHashMap<>();
  private final TimeMeter time;
  private final boolean enabled;

  public RateLimiter(Clock clock, @Value("${app.rate-limit.enabled}") boolean enabled) {
    this.time = new ClockTimeMeter(clock);
    this.enabled = enabled;
  }

  /** Takes one request from the key's allowance, or throws {@link RateLimitedException}. */
  public void consume(Limit limit, String key) {
    if (!enabled) {
      return;
    }
    if (buckets.size() > SWEEP_THRESHOLD) {
      buckets
          .entrySet()
          .removeIf(e -> e.getValue().getAvailableTokens() >= e.getKey().limit().capacity());
    }
    Bucket bucket = buckets.computeIfAbsent(new Key(limit, key), k -> newBucket(limit));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      throw new RateLimitedException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }
  }

  int bucketCount() {
    return buckets.size();
  }

  private Bucket newBucket(Limit limit) {
    return Bucket.builder()
        .addLimit(
            l -> l.capacity(limit.capacity()).refillIntervally(limit.capacity(), limit.period()))
        .withCustomTimePrecision(time)
        .build();
  }

  private record ClockTimeMeter(Clock clock) implements TimeMeter {

    @Override
    public long currentTimeNanos() {
      Instant now = clock.instant();
      return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }

    @Override
    public boolean isWallClockBased() {
      return true;
    }
  }
}
