package com.appointmentprep.common;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * API error codes (TECHNICAL_DESIGN §9.3). Keep in sync with {@code frontend/src/lib/errors.ts},
 * which maps each code to a user-facing message.
 */
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "The request is invalid"),
  INVALID_DURATION(HttpStatus.BAD_REQUEST, "Invalid duration"),
  RANGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "Date range too large"),
  INVALID_TOKEN(HttpStatus.BAD_REQUEST, "Invalid or expired link"),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Not allowed"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
  SLOT_TAKEN(HttpStatus.CONFLICT, "This time slot is no longer available"),
  CUSTOMER_OVERLAP(HttpStatus.CONFLICT, "You already have an appointment at this time"),
  INVALID_STATE(HttpStatus.CONFLICT, "Invalid state for this action"),
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Modified by someone else"),
  EMAIL_TAKEN(HttpStatus.CONFLICT, "Email already registered"),
  OUTSIDE_AVAILABILITY(HttpStatus.UNPROCESSABLE_ENTITY, "Outside the provider's availability"),
  PROVIDER_TIME_OFF(HttpStatus.UNPROCESSABLE_ENTITY, "Provider is on time off"),
  TOO_SOON(HttpStatus.UNPROCESSABLE_ENTITY, "Too soon to book"),
  TOO_FAR(HttpStatus.UNPROCESSABLE_ENTITY, "Too far ahead to book"),
  SERVICE_NOT_OFFERED(HttpStatus.UNPROCESSABLE_ENTITY, "Service not offered by this provider"),
  LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "Booking limit reached"),
  CANCEL_CUTOFF_PASSED(HttpStatus.UNPROCESSABLE_ENTITY, "Cancellation cutoff has passed"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");

  private final HttpStatus status;
  private final String title;

  ErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  /** E.g. {@code https://appointment-prep/errors/slot-taken}. */
  public URI type() {
    return URI.create(
        "https://appointment-prep/errors/" + name().toLowerCase(Locale.ROOT).replace('_', '-'));
  }
}
