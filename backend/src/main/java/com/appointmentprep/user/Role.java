package com.appointmentprep.user;

/** A user has exactly one role in v1 (PRODUCT §2). Stored as text, checked by users_role_valid. */
public enum Role {
  CUSTOMER,
  PROVIDER,
  ADMIN
}
