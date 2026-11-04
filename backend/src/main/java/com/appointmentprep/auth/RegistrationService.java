package com.appointmentprep.auth;

import com.appointmentprep.common.DomainException;
import com.appointmentprep.common.ErrorCode;
import com.appointmentprep.common.IdGenerator;
import com.appointmentprep.user.User;
import com.appointmentprep.user.UserDto;
import com.appointmentprep.user.UserRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer self-registration (FR-1). Providers are invited by admins instead. */
@Service
public class RegistrationService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final IdGenerator ids;
  private final Clock clock;

  public RegistrationService(
      UserRepository users, PasswordEncoder passwordEncoder, IdGenerator ids, Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.ids = ids;
    this.clock = clock;
  }

  @Transactional
  public UserDto register(RegisterRequest request) {
    String email = request.email().trim();
    // Friendly pre-check; the case-insensitive unique constraint on users.email is the real guard.
    if (users.existsByEmailIgnoreCase(email)) {
      throw emailTaken();
    }
    User user =
        User.newCustomer(
            ids.next(),
            email,
            passwordEncoder.encode(request.password()),
            request.fullName().trim(),
            request.timezone(),
            clock.instant());
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw emailTaken();
    }
    return UserDto.from(user);
  }

  private static DomainException emailTaken() {
    return new DomainException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists.");
  }
}
