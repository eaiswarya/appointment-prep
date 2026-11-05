package com.appointmentprep.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.appointmentprep.TestcontainersConfiguration;
import com.appointmentprep.admin.AdminBootstrap.Outcome;
import com.appointmentprep.auth.LoginRequest;
import com.appointmentprep.auth.LoginService;
import com.appointmentprep.auth.RegisterRequest;
import com.appointmentprep.auth.RegistrationService;
import com.appointmentprep.common.DomainException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** The first admin is created from APP_ADMIN_EMAIL / APP_ADMIN_INITIAL_PASSWORD (§12.3). */
@SpringBootTest(
    properties = {
      "app.admin.email=" + AdminBootstrapTest.EMAIL,
      "app.admin.initial-password=" + AdminBootstrapTest.PASSWORD
    })
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
// The startup check must see the state startup left; the other tests change it and restore it.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminBootstrapTest {

  static final String EMAIL = "admin@bootstrap.test";
  static final String PASSWORD = "bootstrap-secret-4f9c2a";

  @Autowired private AdminBootstrap bootstrap;

  @Autowired private LoginService login;

  @Autowired private RegistrationService registration;

  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void restoreTheBootstrapAdmin() {
    removeAdmins();
    bootstrap.ensureAdmin(EMAIL, PASSWORD);
  }

  @Test
  @Order(1)
  void startupCreatesExactlyOneAdminWhoCanLogIn() {
    assertThat(admins()).containsExactly(EMAIL);

    var result = login.login(new LoginRequest(EMAIL, PASSWORD));

    assertThat(result.user().role().name()).isEqualTo("ADMIN");
  }

  @Test
  void runningAgainNeverCreatesASecondAdminOrChangesThePassword() {
    assertThat(bootstrap.ensureAdmin(EMAIL, "a-different-password-9e1")).isEqualTo(Outcome.EXISTS);
    assertThat(bootstrap.ensureAdmin("other@bootstrap.test", "another-password-7b3"))
        .isEqualTo(Outcome.EXISTS);

    assertThat(admins()).containsExactly(EMAIL);
    login.login(new LoginRequest(EMAIL, PASSWORD));
    assertThatThrownBy(() -> login.login(new LoginRequest(EMAIL, "a-different-password-9e1")))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void missingVariablesOnlyWarn(CapturedOutput output) {
    // Only matters on a fresh system: once an admin exists, the variables are never read.
    removeAdmins();

    assertThat(bootstrap.ensureAdmin("", PASSWORD)).isEqualTo(Outcome.SKIPPED);
    assertThat(bootstrap.ensureAdmin(EMAIL, "  ")).isEqualTo(Outcome.SKIPPED);
    assertThat(bootstrap.ensureAdmin(null, null)).isEqualTo(Outcome.SKIPPED);

    assertThat(output).contains("WARN").contains("APP_ADMIN_EMAIL");
  }

  @Test
  void thePasswordNeverAppearsInTheLogs(CapturedOutput output) {
    removeAdmins();

    assertThat(bootstrap.ensureAdmin(EMAIL, PASSWORD)).isEqualTo(Outcome.CREATED);
    bootstrap.ensureAdmin(EMAIL, PASSWORD);
    bootstrap.ensureAdmin(EMAIL, "short");

    assertThat(output).contains(EMAIL).doesNotContain(PASSWORD).doesNotContain("short\"");
  }

  @Test
  void aWeakInitialPasswordIsRefused(CapturedOutput output) {
    removeAdmins();

    assertThat(bootstrap.ensureAdmin(EMAIL, "password1")).isEqualTo(Outcome.SKIPPED);

    assertThat(admins()).isEmpty();
    assertThat(output).contains("WARN").doesNotContain("password1");
  }

  @Test
  void anExistingCustomerEmailIsNeverPromoted(CapturedOutput output) {
    removeAdmins();
    String email = "customer-" + System.nanoTime() + "@bootstrap.test";
    registration.register(new RegisterRequest(email, "correct-horse-battery", "Cust", "UTC"));

    assertThat(bootstrap.ensureAdmin(email, PASSWORD)).isEqualTo(Outcome.SKIPPED);

    assertThat(admins()).isEmpty();
    assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email))
        .isEqualTo("CUSTOMER");
    assertThat(output).contains("WARN");
  }

  @Test
  void twoInstancesStartingAtOnceCreateOneAdmin() throws Exception {
    removeAdmins();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Outcome>> runs =
          List.of(pool.submit(() -> ensureAfter(start)), pool.submit(() -> ensureAfter(start)));
      start.countDown();

      List<Outcome> outcomes =
          List.of(runs.get(0).get(30, TimeUnit.SECONDS), runs.get(1).get(30, TimeUnit.SECONDS));

      assertThat(outcomes).contains(Outcome.CREATED).doesNotContain(Outcome.SKIPPED);
      assertThat(admins()).containsExactly(EMAIL);
    } finally {
      pool.shutdownNow();
    }
  }

  private Outcome ensureAfter(CountDownLatch start) throws InterruptedException {
    start.await();
    return bootstrap.ensureAdmin(EMAIL, PASSWORD);
  }

  private List<String> admins() {
    return jdbc.queryForList("SELECT email FROM users WHERE role = 'ADMIN'", String.class);
  }

  private void removeAdmins() {
    jdbc.update("DELETE FROM users WHERE role = 'ADMIN'");
  }
}
