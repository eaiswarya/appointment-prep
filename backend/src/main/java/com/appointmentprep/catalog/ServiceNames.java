package com.appointmentprep.catalog;

/** Validation for service names, shared by create and update. */
final class ServiceNames {

  /**
   * At least one visible character and no control characters. {@code (?U)} makes {@code \S} and
   * {@code \p{Cntrl}} Unicode-aware: {@code @NotBlank} uses {@code trim()}, which misses spaces
   * such as U+3000, so a name made only of those would otherwise be saved empty after {@code
   * strip()}.
   */
  static final String PATTERN = "(?U)(?s)(?=.*\\S)[^\\p{Cntrl}]*";

  static final String MESSAGE = "must not be blank or contain control characters";

  private ServiceNames() {}
}
