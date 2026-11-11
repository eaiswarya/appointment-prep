package com.appointmentprep.common;

import com.appointmentprep.common.GlobalExceptionHandler.FieldProblem;
import java.util.List;

/**
 * 400 {@code VALIDATION_ERROR} with {@code errors[{field, message}]} for rules that need the
 * database or the whole request, e.g. "these service ids must exist and be active".
 */
public class FieldValidationException extends DomainException {

  private final List<FieldProblem> errors;

  public FieldValidationException(String field, String message) {
    super(ErrorCode.VALIDATION_ERROR, "Some fields are invalid.");
    this.errors = List.of(new FieldProblem(field, message));
  }

  public List<FieldProblem> errors() {
    return errors;
  }
}
