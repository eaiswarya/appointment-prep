package com.appointmentprep.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of the current time. Inject {@link Clock} instead of calling {@code
 * Instant.now()}, so tests can freeze time.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
