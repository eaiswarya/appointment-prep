package com.appointmentprep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.appointmentprep.common.DomainException;
import com.appointmentprep.common.MutableClock;
import com.appointmentprep.notification.EmailOutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** Forgot / reset password (FR-3; TECHNICAL_DESIGN §8.1, §7.1). */
@SpringBootTest(properties = "app.base-url=https://app.example.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetTest {

  private static final Instant T0 = Instant.parse("2026-11-03T04:30:00Z");
  private static final MutableClock CLOCK = new MutableClock(T0);
  private static final String OLD_PASSWORD = "correct-horse-battery";
  private static final String NEW_PASSWORD = "a-brand-new-passphrase";

  @TestBean private Clock clock;

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private RegistrationService registration;

  @Autowired private PasswordResetService passwordReset;

  @Autowired private TransactionTemplate transactions;

  @Autowired private ObjectMapper json;

  @MockitoSpyBean private EmailOutboxRepository outbox;

  static Clock clock() {
    return CLOCK;
  }

  @BeforeEach
  void resetClock() {
    CLOCK.set(T0);
  }

  @Test
  void knownAndUnknownEmailsLookTheSame() throws Exception {
    String known = registerCustomer();
    String unknown = "nobody-" + UUID.randomUUID() + "@example.com";

    MvcResult a = forgot(known).andExpect(status().isAccepted()).andReturn();
    MvcResult b = forgot(unknown).andExpect(status().isAccepted()).andReturn();

    assertThat(a.getResponse().getContentAsString())
        .isEqualTo(b.getResponse().getContentAsString());
    assertThat(outboxRows(known)).hasSize(1);
    assertThat(outboxRows(unknown)).isEmpty();
    assertThat(resetTokens(known)).isEqualTo(1);

    Map<String, Object> row = outboxRows(known).get(0);
    assertThat(row.get("template")).isEqualTo("PASSWORD_RESET");
    assertThat(row.get("status")).isEqualTo("PENDING");
    JsonNode payload = json.readTree(row.get("payload").toString());
    assertThat(payload.path("resetLink").asText())
        // In the fragment, never the query: fragments never reach a server or its access log.
        .startsWith("https://app.example.test/reset-password#token=");
    assertThat(payload.path("expiresAt").asText())
        .isEqualTo(T0.plus(Duration.ofHours(1)).toString());
    assertThat(payload.path("timezone").asText()).isEqualTo("Asia/Kolkata");
  }

  @Test
  void resetChangesThePasswordAndEndsSessions() throws Exception {
    String email = registerCustomer();
    String refreshCookie = loginCookie(email, OLD_PASSWORD);
    forgot(email).andExpect(status().isAccepted());

    reset(tokenFromOutbox(email), NEW_PASSWORD).andExpect(status().isNoContent());

    login(email, NEW_PASSWORD).andExpect(status().isOk());
    login(email, OLD_PASSWORD).andExpect(status().isUnauthorized());
    mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", refreshCookie)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void usedTokenIsInvalid() throws Exception {
    String email = registerCustomer();
    forgot(email);
    String token = tokenFromOutbox(email);
    reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

    reset(token, "yet-another-passphrase")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void expiredTokenIsInvalid() throws Exception {
    String email = registerCustomer();
    forgot(email);
    String token = tokenFromOutbox(email);

    CLOCK.advance(Duration.ofMinutes(61));

    reset(token, NEW_PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    login(email, OLD_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void unknownTokenIsInvalid() throws Exception {
    reset("not-a-real-token", NEW_PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void aNewRequestInvalidatesTheEarlierLink() throws Exception {
    String email = registerCustomer();
    forgot(email);
    String first = tokenFromOutbox(email);
    forgot(email);

    reset(first, NEW_PASSWORD).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void anInviteTokenCannotResetAPassword() throws Exception {
    String email = registerCustomer();
    String raw = "invite-token-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO user_tokens (id, user_id, type, token_hash, expires_at)"
            + " SELECT ?, id, 'INVITE', ?, ? FROM users WHERE email = ?",
        UUID.randomUUID(),
        OpaqueTokens.sha256(raw),
        Timestamp.from(T0.plus(Duration.ofHours(72))),
        email);

    reset(raw, NEW_PASSWORD).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void weakNewPasswordIsAValidationError() throws Exception {
    String email = registerCustomer();
    forgot(email);

    reset(tokenFromOutbox(email), "password1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("newPassword")));
  }

  @Test
  void aRolledBackRequestLeavesNoOutboxRow() {
    String email = registerCustomer();

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    tx -> {
                      passwordReset.requestReset(email);
                      throw new IllegalStateException("something later in the transaction failed");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(outboxRows(email)).isEmpty();
    assertThat(resetTokens(email)).isZero();
  }

  @Test
  void aFailedOutboxWriteRollsBackTheToken() throws Exception {
    // The outbox row and the token commit together or not at all (BEFORE_COMMIT, same transaction).
    String email = registerCustomer();
    doThrow(new IllegalStateException("outbox unavailable")).when(outbox).save(any());

    forgot(email).andExpect(status().isInternalServerError());

    assertThat(resetTokens(email)).isZero();
    assertThat(outboxRows(email)).isEmpty();
  }

  @Test
  void inactiveAndInvitedAccountsGetNoLink() throws Exception {
    String inactive = registerCustomer();
    jdbc.update("UPDATE users SET active = false WHERE email = ?", inactive);
    String invited = "invited-" + UUID.randomUUID() + "@example.com";
    jdbc.update(
        "INSERT INTO users (id, email, full_name, role) VALUES (?, ?, 'Invited', 'PROVIDER')",
        UUID.randomUUID(),
        invited);

    forgot(inactive).andExpect(status().isAccepted());
    forgot(invited).andExpect(status().isAccepted());

    assertThat(outboxRows(inactive)).isEmpty();
    assertThat(outboxRows(invited)).isEmpty();
    assertThat(resetTokens(inactive)).isZero();
    assertThat(resetTokens(invited)).isZero();
  }

  @Test
  void aUserDeactivatedAfterTheLinkWasSentCannotUseIt() throws Exception {
    String email = registerCustomer();
    forgot(email);
    String token = tokenFromOutbox(email);
    jdbc.update("UPDATE users SET active = false WHERE email = ?", email);

    reset(token, NEW_PASSWORD).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void aLinkCanBeRedeemedOnlyOnceEvenConcurrently() throws Exception {
    String email = registerCustomer();
    forgot(email);
    String token = tokenFromOutbox(email);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<String>> runs =
          List.of(
              pool.submit(() -> redeemAfter(start, token, "first-new-passphrase")),
              pool.submit(() -> redeemAfter(start, token, "second-new-passphrase")));
      start.countDown();
      List<String> outcomes =
          List.of(runs.get(0).get(30, TimeUnit.SECONDS), runs.get(1).get(30, TimeUnit.SECONDS));

      assertThat(outcomes).containsExactlyInAnyOrder("OK", "INVALID_TOKEN");
    } finally {
      pool.shutdownNow();
    }
  }

  private String redeemAfter(CountDownLatch start, String token, String password)
      throws InterruptedException {
    start.await();
    try {
      passwordReset.resetPassword(token, password);
      return "OK";
    } catch (DomainException e) {
      return e.code().name();
    }
  }

  private ResultActions forgot(String email) throws Exception {
    return mvc.perform(
        post("/auth/forgot-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email))));
  }

  private ResultActions reset(String token, String newPassword) throws Exception {
    return mvc.perform(
        post("/auth/reset-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mvc.perform(
        post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email, "password", password))));
  }

  private String loginCookie(String email, String password) throws Exception {
    String header =
        login(email, password)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.SET_COOKIE);
    return header.substring("refresh_token=".length(), header.indexOf(';'));
  }

  private String registerCustomer() {
    String email = "user-" + UUID.randomUUID() + "@example.com";
    registration.register(new RegisterRequest(email, OLD_PASSWORD, "Asha", "Asia/Kolkata"));
    return email;
  }

  private List<Map<String, Object>> outboxRows(String email) {
    return jdbc.queryForList(
        "SELECT template, status, payload::text AS payload FROM email_outbox"
            + " WHERE recipient = ? ORDER BY created_at, id",
        email);
  }

  private int resetTokens(String email) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM user_tokens t JOIN users u ON u.id = t.user_id"
            + " WHERE u.email = ? AND t.type = 'PASSWORD_RESET'",
        Integer.class,
        email);
  }

  /** The newest reset link's token, as the email would carry it. */
  private String tokenFromOutbox(String email) throws Exception {
    List<Map<String, Object>> rows = outboxRows(email);
    String link =
        json.readTree(rows.get(rows.size() - 1).get("payload").toString())
            .path("resetLink")
            .asText();
    return link.substring(link.indexOf("#token=") + "#token=".length());
  }
}
