package com.appointmentprep.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Pins the codes and statuses to TECHNICAL_DESIGN §9.3. Change both together. */
class ErrorCodeTest {

  private static final Map<String, Integer> DESIGN_9_3 =
      Map.ofEntries(
          Map.entry("VALIDATION_ERROR", 400),
          Map.entry("INVALID_DURATION", 400),
          Map.entry("RANGE_TOO_LARGE", 400),
          Map.entry("UNAUTHENTICATED", 401),
          Map.entry("TOKEN_EXPIRED", 401),
          Map.entry("FORBIDDEN", 403),
          Map.entry("NOT_FOUND", 404),
          Map.entry("SLOT_TAKEN", 409),
          Map.entry("CUSTOMER_OVERLAP", 409),
          Map.entry("INVALID_STATE", 409),
          Map.entry("CONCURRENT_MODIFICATION", 409),
          Map.entry("EMAIL_TAKEN", 409),
          Map.entry("OUTSIDE_AVAILABILITY", 422),
          Map.entry("PROVIDER_TIME_OFF", 422),
          Map.entry("TOO_SOON", 422),
          Map.entry("TOO_FAR", 422),
          Map.entry("SERVICE_NOT_OFFERED", 422),
          Map.entry("LIMIT_REACHED", 422),
          Map.entry("CANCEL_CUTOFF_PASSED", 422),
          Map.entry("RATE_LIMITED", 429));

  @Test
  void codesAndStatusesMatchTheDesign() {
    Map<String, Integer> actual =
        Arrays.stream(ErrorCode.values())
            .collect(Collectors.toMap(ErrorCode::name, code -> code.status().value()));

    assertThat(actual).containsExactlyInAnyOrderEntriesOf(DESIGN_9_3);
  }

  @Test
  void typeUriIsKebabCaseOfTheCode() {
    assertThat(ErrorCode.SLOT_TAKEN.type())
        .hasToString("https://appointment-prep/errors/slot-taken");
  }
}
