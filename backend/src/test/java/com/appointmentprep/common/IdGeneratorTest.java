package com.appointmentprep.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

  private static final Instant T = Instant.parse("2026-11-03T04:30:00Z");

  @Test
  void generatesVersion7WithTheClockTimestamp() {
    UUID id = new IdGenerator(Clock.fixed(T, ZoneOffset.UTC)).next();

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(T.toEpochMilli());
  }

  @Test
  void idsAtTheSameInstantSortInCreationOrder() {
    IdGenerator ids = new IdGenerator(Clock.fixed(T, ZoneOffset.UTC));

    List<UUID> generated = new ArrayList<>();
    for (int i = 0; i < 5000; i++) {
      generated.add(ids.next());
    }

    // Postgres orders uuid byte-wise, which matches the order of their string forms.
    List<String> asStrings = generated.stream().map(UUID::toString).toList();
    assertThat(asStrings).isSorted().doesNotHaveDuplicates();
  }

  @Test
  void laterInstantsSortAfterEarlierOnes() {
    UUID earlier = new IdGenerator(Clock.fixed(T, ZoneOffset.UTC)).next();
    UUID later = new IdGenerator(Clock.fixed(T.plusMillis(1), ZoneOffset.UTC)).next();

    assertThat(later.toString()).isGreaterThan(earlier.toString());
  }
}
