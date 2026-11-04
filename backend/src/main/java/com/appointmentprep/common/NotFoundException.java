package com.appointmentprep.common;

/** 404 {@code NOT_FOUND}. Also used for resources the caller doesn't own (404, not 403). */
public class NotFoundException extends DomainException {

  public NotFoundException(String detail) {
    super(ErrorCode.NOT_FOUND, detail);
  }
}
