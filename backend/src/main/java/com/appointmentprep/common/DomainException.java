package com.appointmentprep.common;

/**
 * A business rule failure with an API error code. {@link GlobalExceptionHandler} turns it into
 * problem+json; throw it (or a named subclass) instead of adding per-exception handlers.
 */
public class DomainException extends RuntimeException {

  private final ErrorCode code;

  public DomainException(ErrorCode code) {
    this(code, code.title());
  }

  public DomainException(ErrorCode code, String detail) {
    super(detail);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
