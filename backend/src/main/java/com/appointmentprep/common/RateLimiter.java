package com.appointmentprep.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
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
 * IP). Each limit allows {@code capacity} requests per fixed {@code period}; the windows are
 * aligned to the key's first request. Buckets read the injected {@link Clock}, so tests move time
 * instead of sleeping.
 */
@Component
public class RateLimiter {

  /** A named limit: {@code capacity} requests per {@code period}. */
  public record Limit(String name, int capacity, Duration period) {}

  // Caps memory if very many addresses show up at once; past it, the least used buckets go first.
  private static final long MAX_BUCKETS_PER_LIMIT = 100_000;

  private final ConcurrentMap<Limit, Cache<String, Bucket>> buckets = new ConcurrentHashMap<>();
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
    Bucket bucket = buckets.computeIfAbsent(limit, this::newCache).get(key, k -> newBucket(limit));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      throw new RateLimitedException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }
  }

  long bucketCount() {
    long count = 0;
    for (Cache<String, Bucket> cache : buckets.values()) {
      cache.cleanUp(); // run pending expiry now
      count += cache.estimatedSize();
    }
    return count;
  }

  /**
   * A bucket untouched for a whole period has passed a refill, so it is full again: forgetting it
   * changes nothing, and the next request starts a fresh one.
   */
  private Cache<String, Bucket> newCache(Limit limit) {
    return Caffeine.newBuilder()
        .expireAfterAccess(limit.period())
        .maximumSize(MAX_BUCKETS_PER_LIMIT)
        .ticker((Ticker) time::currentTimeNanos)
        .build();
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
