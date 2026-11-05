package com.appointmentprep.admin;

import com.appointmentprep.auth.StrongPassword;
import com.appointmentprep.common.IdGenerator;
import com.appointmentprep.user.Role;
import com.appointmentprep.user.User;
import com.appointmentprep.user.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin on startup from {@code APP_ADMIN_EMAIL} and {@code
 * APP_ADMIN_INITIAL_PASSWORD} (TECHNICAL_DESIGN §12.3), because admins can't register themselves.
 * It never touches an existing admin, never promotes an existing account, and never logs the
 * password.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

  public enum Outcome {
    /** A new admin was created. */
    CREATED,
    /** An admin already exists (or another instance just created it); nothing changed. */
    EXISTS,
    /** Nothing was created: missing or unusable configuration. A warning explains why. */
    SKIPPED
  }

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final IdGenerator ids;
  private final Clock clock;
  private final String email;
  private final String initialPassword;

  public AdminBootstrap(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      IdGenerator ids,
      Clock clock,
      @Value("${app.admin.email:}") String email,
      @Value("${app.admin.initial-password:}") String initialPassword) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.ids = ids;
    this.clock = clock;
    this.email = email;
    this.initialPassword = initialPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    ensureAdmin(email, initialPassword);
  }

  /**
   * Not {@code @Transactional}: a duplicate insert from a concurrent instance must fail in its own
   * repository transaction so it can be treated as "already exists" here.
   */
  public Outcome ensureAdmin(String email, String password) {
    if (users.existsByRole(Role.ADMIN)) {
      return Outcome.EXISTS;
    }
    if (email == null || email.isBlank() || password == null || password.isBlank()) {
      log.warn(
          "No admin exists and APP_ADMIN_EMAIL / APP_ADMIN_INITIAL_PASSWORD are not both set;"
              + " skipping admin bootstrap.");
      return Outcome.SKIPPED;
    }
    String trimmedEmail = email.trim();
    Optional<String> weak = StrongPassword.Validator.problem(password);
    if (weak.isPresent()) {
      log.warn("APP_ADMIN_INITIAL_PASSWORD {}; skipping admin bootstrap.", weak.get());
      return Outcome.SKIPPED;
    }
    if (users.emailExists(trimmedEmail)) {
      log.warn(
          "APP_ADMIN_EMAIL {} already belongs to a non-admin account; not promoting it.",
          trimmedEmail);
      return Outcome.SKIPPED;
    }
    User admin =
        User.newAdmin(
            ids.next(),
            trimmedEmail,
            passwordEncoder.encode(password),
            "Administrator",
            "UTC",
            clock.instant());
    try {
      users.saveAndFlush(admin);
    } catch (DataIntegrityViolationException e) {
      // Another instance created it at the same moment (unique email).
      log.info("Admin {} was created by another instance.", trimmedEmail);
      return Outcome.EXISTS;
    }
    log.info("Created the bootstrap admin {}.", trimmedEmail);
    return Outcome.CREATED;
  }
}
