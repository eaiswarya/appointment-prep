package com.appointmentprep.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  /** Case-insensitive via citext, and uses the users_email_unique index. */
  @Query(
      value = "SELECT EXISTS (SELECT 1 FROM users WHERE email = CAST(:email AS citext))",
      nativeQuery = true)
  boolean emailExists(@Param("email") String email);
}
