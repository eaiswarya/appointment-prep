package com.appointmentprep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.appointmentprep.common.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** Login, access tokens and URL rules (FR-2, FR-4; TECHNICAL_DESIGN §8.1, §8.2). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginTest {

  private static final Instant T0 = Instant.parse("2026-11-03T04:30:00Z");
  private static final MutableClock CLOCK = new MutableClock(T0);
  private static final String PASSWORD = "correct-horse-battery";

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
  void loginReturnsTokenUserAndRefreshCookie() throws Exception {
    String email = registerCustomer("Asha Rao");

    MvcResult result =
        login(email, PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.fullName").value("Asha Rao"))
            .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
            .andReturn();

    String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie)
        .startsWith("refresh_token=")
        .contains("Path=/api/auth")
        .contains("Max-Age=604800")
        .contains("Secure")
        .contains("HttpOnly")
        .contains("SameSite=Strict");

    // Only the SHA-256 of the opaque token is stored, expiring 7 days from the injected clock.
    String raw = cookie.substring("refresh_token=".length(), cookie.indexOf(';'));
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT t.token_hash, t.expires_at FROM refresh_tokens t"
                + " JOIN users u ON u.id = t.user_id WHERE u.email = ?",
            email);
    assertThat(((String) row.get("token_hash")).trim()).hasSize(64).isNotEqualTo(raw);
    assertThat(((Timestamp) row.get("expires_at")).toInstant())
        .isEqualTo(T0.plus(Duration.ofDays(7)));
  }

  @Test
  void badCredentialsAllLookTheSame() throws Exception {
    String email = registerCustomer("Asha");
    String inactive = registerCustomer("Gone");
    jdbc.update("UPDATE users SET active = false WHERE email = ?", inactive);

    List<JsonNode> bodies =
        List.of(
            failedLogin(email, "wrong-password-123"),
            failedLogin("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD),
            failedLogin(inactive, PASSWORD));

    for (JsonNode body : bodies) {
      assertThat(body.path("code").asText()).isEqualTo("UNAUTHENTICATED");
    }
    // Identical apart from the per-request ID, so callers can't tell which case it was.
    List<JsonNode> withoutRequestId =
        bodies.stream()
            .map(b -> ((ObjectNode) b).without("requestId"))
            .map(JsonNode.class::cast)
            .toList();
    assertThat(withoutRequestId).containsOnly(withoutRequestId.get(0));
  }

  @Test
  void meReturnsTheUserForAValidToken() throws Exception {
    String email = registerCustomer("Asha Rao");
    String token = accessToken(email);

    mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("CUSTOMER"));
  }

  @Test
  void meWithoutATokenIsUnauthenticated() throws Exception {
    mvc.perform(get("/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void meWithAnExpiredTokenIsTokenExpired() throws Exception {
    String token = accessToken(registerCustomer("Asha"));

    CLOCK.advance(Duration.ofMinutes(16));

    mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
  }

  @Test
  void tamperedTokenIsUnauthenticated() throws Exception {
    String token = accessToken(registerCustomer("Asha"));
    String tampered = token.substring(0, token.length() - 4) + "AAAA";

    mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void customerIsForbiddenFromAdminPaths() throws Exception {
    String token = accessToken(registerCustomer("Asha"));

    mvc.perform(get("/admin/anything").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void customerIsForbiddenFromProviderPaths() throws Exception {
    String token = accessToken(registerCustomer("Asha"));

    mvc.perform(get("/provider/anything").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void invitedUserWithoutAPasswordCannotLogIn() throws Exception {
    // A provider who hasn't accepted their invite has no password hash yet.
    String email = "invited-" + UUID.randomUUID() + "@example.com";
    jdbc.update(
        "INSERT INTO users (id, email, full_name, role) VALUES (?, ?, 'Invited', 'PROVIDER')",
        UUID.randomUUID(),
        email);

    login(email, PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void anExpiredBearerTokenDoesNotBlockLogin() throws Exception {
    // The SPA may still hold an expired token; /auth/** must ignore it, not answer TOKEN_EXPIRED.
    String email = registerCustomer("Asha");
    String token = accessToken(email);
    CLOCK.advance(Duration.ofMinutes(16));

    String body = json.writeValueAsString(Map.of("email", email, "password", PASSWORD));
    mvc.perform(
            post("/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void invalidTokenChallengeNamesTheError() throws Exception {
    mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
  }

  @Test
  void healthIsPublicButInfoIsNot() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
  }

  @Test
  void loginIsPublicAndExplainsFailureGenerically() throws Exception {
    login("nobody@example.com", PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.detail", containsString("email or password")));
  }

  private String registerCustomer(String name) {
    String email = "user-" + UUID.randomUUID() + "@example.com";
    registration.register(new RegisterRequest(email, PASSWORD, name, "UTC"));
    return email;
  }

  private ResultActions login(String email, String password) throws Exception {
    String body = json.writeValueAsString(Map.of("email", email, "password", password));
    return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private JsonNode failedLogin(String email, String password) throws Exception {
    String body =
        login(email, password)
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private String accessToken(String email) throws Exception {
    String body =
        login(email, PASSWORD)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).path("accessToken").asText();
  }
}
