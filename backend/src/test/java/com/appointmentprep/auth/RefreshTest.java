package com.appointmentprep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.appointmentprep.common.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Refresh rotation, reuse detection and logout (FR-2; TECHNICAL_DESIGN §8.1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshTest {

  private static final Instant T0 = Instant.parse("2026-11-03T04:30:00Z");
  private static final MutableClock CLOCK = new MutableClock(T0);
  private static final String PASSWORD = "correct-horse-battery";
  private static final String COOKIE = "refresh_token";

  @TestBean private Clock clock;

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private RegistrationService registration;

  @Autowired private ObjectMapper json;

  static Clock clock() {
    return CLOCK;
  }

  @BeforeEach
  void resetClock() {
    CLOCK.set(T0);
  }

  @Test
  void refreshRotatesTheToken() throws Exception {
    String email = registerCustomer();
    String first = loginCookie(email);

    MvcResult result =
        refresh(first)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

    String second = cookieValue(result);
    assertThat(second).isNotBlank().isNotEqualTo(first);
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
        .contains("Path=/api/auth")
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict");

    Map<String, Object> old = row(first);
    Map<String, Object> current = row(second);
    assertThat(old.get("revoked_at")).isNotNull();
    assertThat(old.get("replaced_by")).isEqualTo(current.get("id"));
    assertThat(current.get("revoked_at")).isNull();

    // The new access token works.
    String accessToken =
        json.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk());
  }

  @Test
  void reusingARotatedTokenRevokesEverything() throws Exception {
    String email = registerCustomer();
    String first = loginCookie(email);
    String second = cookieValue(refresh(first).andExpect(status().isOk()).andReturn());
    String otherDevice = loginCookie(email);

    // Someone replays the old token after the grace window: treat it as theft.
    CLOCK.advance(RefreshTokenService.REUSE_GRACE.plusSeconds(1));
    refresh(first)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

    Integer stillValid =
        jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id"
                + " WHERE u.email = ? AND t.revoked_at IS NULL",
            Integer.class,
            email);
    assertThat(stillValid).isZero();
    refresh(second).andExpect(status().isUnauthorized());
    refresh(otherDevice).andExpect(status().isUnauthorized());
  }

  @Test
  void replayWithinTheGraceWindowIsRejectedWithoutRevokingTheSession() throws Exception {
    // A second tab or an aborted response re-sends the old cookie moments after a rotation.
    String first = loginCookie(registerCustomer());
    String second = cookieValue(refresh(first).andExpect(status().isOk()).andReturn());

    CLOCK.advance(Duration.ofSeconds(10));
    refresh(first)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

    refresh(second).andExpect(status().isOk());
  }

  @Test
  void replayAfterTheReplacementWasUsedIsTheftEvenWithinTheGraceWindow() throws Exception {
    String email = registerCustomer();
    String first = loginCookie(email);
    String second = cookieValue(refresh(first).andExpect(status().isOk()).andReturn());
    String third = cookieValue(refresh(second).andExpect(status().isOk()).andReturn());

    refresh(first).andExpect(status().isUnauthorized());

    refresh(third).andExpect(status().isUnauthorized());
  }

  @Test
  void concurrentRefreshesWithOneCookieKeepTheUserLoggedIn() throws Exception {
    String email = registerCustomer();
    String cookie = loginCookie(email);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<MvcResult>> calls =
          List.of(
              pool.submit(() -> refreshAfter(start, cookie)),
              pool.submit(() -> refreshAfter(start, cookie)));
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      String winner = null;
      for (Future<MvcResult> call : calls) {
        MvcResult result = call.get(30, TimeUnit.SECONDS);
        statuses.add(result.getResponse().getStatus());
        if (result.getResponse().getStatus() == 200) {
          winner = cookieValue(result);
        }
      }

      // The row lock lets exactly one rotate; the other is a harmless loser, not a theft.
      assertThat(statuses).containsExactlyInAnyOrder(200, 401);
      refresh(winner).andExpect(status().isOk());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void expiredTokenIsRejected() throws Exception {
    String cookie = loginCookie(registerCustomer());

    CLOCK.advance(Duration.ofDays(7));

    refresh(cookie)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void unknownTokenIsRejected() throws Exception {
    refresh("not-a-real-token")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void missingCookieIsRejected() throws Exception {
    mvc.perform(post("/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void deactivatedUserCannotRefresh() throws Exception {
    String email = registerCustomer();
    String cookie = loginCookie(email);
    jdbc.update("UPDATE users SET active = false WHERE email = ?", email);

    refresh(cookie).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesTheTokenAndClearsTheCookie() throws Exception {
    String cookie = loginCookie(registerCustomer());

    MvcResult result =
        mvc.perform(post("/auth/logout").cookie(new Cookie(COOKIE, cookie)))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
        .startsWith(COOKIE + "=;")
        .contains("Max-Age=0")
        .contains("Path=/api/auth");
    assertThat(row(cookie).get("revoked_at")).isNotNull();
    refresh(cookie).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutWithoutACookieStillSucceeds() throws Exception {
    mvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
  }

  private String registerCustomer() {
    String email = "user-" + UUID.randomUUID() + "@example.com";
    registration.register(new RegisterRequest(email, PASSWORD, "Asha", "UTC"));
    return email;
  }

  private String loginCookie(String email) throws Exception {
    String body = json.writeValueAsString(Map.of("email", email, "password", PASSWORD));
    MvcResult result =
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();
    return cookieValue(result);
  }

  private ResultActions refresh(String cookie) throws Exception {
    return mvc.perform(post("/auth/refresh").cookie(new Cookie(COOKIE, cookie)));
  }

  private MvcResult refreshAfter(CountDownLatch start, String cookie) throws Exception {
    start.await();
    return refresh(cookie).andReturn();
  }

  private static String cookieValue(MvcResult result) {
    List<String> headers = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    String header = headers.get(headers.size() - 1);
    return header.substring(COOKIE.length() + 1, header.indexOf(';'));
  }

  private Map<String, Object> row(String rawCookie) {
    return jdbc.queryForMap(
        "SELECT id, revoked_at, replaced_by FROM refresh_tokens WHERE token_hash = ?",
        RefreshTokenService.sha256(rawCookie));
  }
}
