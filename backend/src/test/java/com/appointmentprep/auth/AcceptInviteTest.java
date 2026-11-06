package com.appointmentprep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.appointmentprep.common.DomainException;
import com.appointmentprep.common.MutableClock;
import com.appointmentprep.provider.InviteProviderRequest;
import com.appointmentprep.provider.ProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Invited providers set their password (FR-1; TECHNICAL_DESIGN §8.1, §9.1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AcceptInviteTest {

  private static final Instant T0 = Instant.parse("2026-11-03T04:30:00Z");
  private static final MutableClock CLOCK = new MutableClock(T0);
  private static final String PASSWORD = "a-brand-new-passphrase";

  @TestBean private Clock clock;

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private ProviderService providers;

  @Autowired private InviteService invites;

  @Autowired private ObjectMapper json;

  static Clock clock() {
    return CLOCK;
  }

  @BeforeEach
  void resetClock() {
    CLOCK.set(T0);
  }

  @Test
  void acceptingSetsThePasswordAndAllowsLogin() throws Exception {
    String email = inviteProvider();

    accept(inviteToken(email), PASSWORD).andExpect(status().isNoContent());

    login(email, PASSWORD)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.role").value("PROVIDER"))
        .andExpect(jsonPath("$.user.email").value(email));
    assertThat(
            jdbc.queryForObject(
                "SELECT used_at FROM user_tokens t JOIN users u ON u.id = t.user_id"
                    + " WHERE u.email = ? AND t.type = 'INVITE'",
                Timestamp.class,
                email))
        .isNotNull();
  }

  @Test
  void usedInviteIsInvalid() throws Exception {
    String email = inviteProvider();
    String token = inviteToken(email);
    accept(token, PASSWORD).andExpect(status().isNoContent());

    accept(token, "another-new-passphrase")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    login(email, PASSWORD).andExpect(status().isOk());
  }

  @Test
  void expiredInviteIsInvalid() throws Exception {
    String email = inviteProvider();
    String token = inviteToken(email);

    CLOCK.advance(Duration.ofHours(73));

    accept(token, PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    login(email, PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void unknownTokenIsInvalid() throws Exception {
    accept("not-a-real-token", PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void weakPasswordIsAValidationError() throws Exception {
    String email = inviteProvider();

    accept(inviteToken(email), "password1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
  }

  @Test
  void aResetTokenCannotAcceptAnInvite() throws Exception {
    String email = inviteProvider();
    String raw = "reset-token-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO user_tokens (id, user_id, type, token_hash, expires_at)"
            + " SELECT ?, id, 'PASSWORD_RESET', ?, ? FROM users WHERE email = ?",
        UUID.randomUUID(),
        OpaqueTokens.sha256(raw),
        Timestamp.from(T0.plus(Duration.ofHours(1))),
        email);

    accept(raw, PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    login(email, PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void aDeactivatedProviderCannotAccept() throws Exception {
    String email = inviteProvider();
    String token = inviteToken(email);
    jdbc.update("UPDATE users SET active = false WHERE email = ?", email);

    accept(token, PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    login(email, PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void anInviteCannotOverwriteAnExistingPassword() throws Exception {
    // E.g. a stray older invite for a provider who has already set up their account.
    String email = inviteProvider();
    accept(inviteToken(email), PASSWORD).andExpect(status().isNoContent());
    String raw = "stray-invite-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO user_tokens (id, user_id, type, token_hash, expires_at)"
            + " SELECT ?, id, 'INVITE', ?, ? FROM users WHERE email = ?",
        UUID.randomUUID(),
        OpaqueTokens.sha256(raw),
        Timestamp.from(T0.plus(Duration.ofHours(72))),
        email);

    accept(raw, "attacker-chosen-passphrase")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    login(email, PASSWORD).andExpect(status().isOk());
  }

  @Test
  void anInviteCanBeAcceptedOnlyOnceEvenConcurrently() throws Exception {
    String token = inviteToken(inviteProvider());
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<String>> runs =
          List.of(
              pool.submit(() -> acceptAfter(start, token, "first-new-passphrase")),
              pool.submit(() -> acceptAfter(start, token, "second-new-passphrase")));
      start.countDown();

      List<String> outcomes =
          List.of(runs.get(0).get(30, TimeUnit.SECONDS), runs.get(1).get(30, TimeUnit.SECONDS));

      assertThat(outcomes).containsExactlyInAnyOrder("OK", "INVALID_TOKEN");
    } finally {
      pool.shutdownNow();
    }
  }

  private String acceptAfter(CountDownLatch start, String token, String password)
      throws InterruptedException {
    start.await();
    try {
      invites.acceptInvite(token, password);
      return "OK";
    } catch (DomainException e) {
      return e.code().name();
    }
  }

  private String inviteProvider() {
    String email = "dr-" + UUID.randomUUID() + "@example.com";
    providers.invite(new InviteProviderRequest(email, "Dr Test", "Dr Test", "Asia/Kolkata"));
    return email;
  }

  /** The token from the invite email's link, as the provider would click it. */
  private String inviteToken(String email) throws Exception {
    String payload =
        jdbc.queryForObject(
            "SELECT payload::text FROM email_outbox WHERE recipient = ? AND template = 'ACCOUNT_INVITE'",
            String.class,
            email);
    String link = json.readTree(payload).path("inviteLink").asText();
    return link.substring(link.indexOf("#token=") + "#token=".length());
  }

  private ResultActions accept(String token, String password) throws Exception {
    return mvc.perform(
        post("/auth/accept-invite")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("token", token, "password", password))));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mvc.perform(
        post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email, "password", password))));
  }
}
