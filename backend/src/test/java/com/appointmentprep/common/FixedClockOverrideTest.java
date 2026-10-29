package com.appointmentprep.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;

/** Shows how tests freeze time: replace the {@link Clock} bean with a fixed clock. */
@SpringBootTest
class FixedClockOverrideTest {

  private static final Instant FIXED = Instant.parse("2026-11-03T04:30:00Z");

  @TestBean private Clock clock;

  @Autowired private Clock injected;

  static Clock clock() {
    return Clock.fixed(FIXED, ZoneOffset.UTC);
  }

  @Test
  void injectedClockIsTheFixedClock() {
    assertThat(injected.instant()).isEqualTo(FIXED);
  }
}
