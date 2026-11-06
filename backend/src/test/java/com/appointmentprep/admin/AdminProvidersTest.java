package com.appointmentprep.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.appointmentprep.auth.RegisterRequest;
import com.appointmentprep.auth.RegistrationService;
import com.appointmentprep.notification.EmailOutboxRepository;
import com.appointmentprep.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Admins invite providers (FR-1, A1; TECHNICAL_DESIGN §8.1, §9.1). */
@SpringBootTest(properties = "app.base-url=https://app.example.test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminProvidersTest {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private RegistrationService registration;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper json;

  @MockitoSpyBean private EmailOutboxRepository outbox;

  @MockitoSpyBean private UserRepository users;

  private String adminToken;

  @BeforeEach
  void logInAsAdmin() throws Exception {
    String email = "admin-" + UUID.randomUUID() + "@example.com";
    insertUser(email, "ADMIN", passwordEncoder.encode(PASSWORD));
    adminToken = accessToken(email);
  }

  @Test
  void invitingCreatesUserProviderTokenAndEmail() throws Exception {
    String email = uniqueEmail("dr");

    invite(adminToken, email, "Dr Meera Iyer", "Dr Iyer", "Asia/Kolkata")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.fullName").value("Dr Meera Iyer"))
        .andExpect(jsonPath("$.displayName").value("Dr Iyer"))
        .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
        .andExpect(jsonPath("$.invitePending").value(true));

    Map<String, Object> user =
        jdbc.queryForMap("SELECT id, role, password_hash FROM users WHERE email = ?", email);
    assertThat(user.get("role")).isEqualTo("PROVIDER");
    assertThat(user.get("password_hash")).isNull();

    Map<String, Object> provider =
        jdbc.queryForMap(
            "SELECT display_name, timezone, buffer_minutes, active FROM providers WHERE user_id = ?",
            user.get("id"));
    assertThat(provider)
        .containsEntry("display_name", "Dr Iyer")
        .containsEntry("timezone", "Asia/Kolkata")
        .containsEntry("buffer_minutes", 0)
        .containsEntry("active", true);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_tokens WHERE user_id = ? AND type = 'INVITE' AND used_at IS NULL",
                Integer.class,
                user.get("id")))
        .isEqualTo(1);

    Map<String, Object> emailRow =
        jdbc.queryForMap(
            "SELECT template, status, payload::text AS payload FROM email_outbox WHERE recipient = ?",
            email);
    assertThat(emailRow.get("template")).isEqualTo("ACCOUNT_INVITE");
    assertThat(emailRow.get("status")).isEqualTo("PENDING");
    JsonNode payload = json.readTree(emailRow.get("payload").toString());
    // The token goes in the fragment, as for reset links (§8.1).
    assertThat(payload.path("inviteLink").asText())
        .startsWith("https://app.example.test/accept-invite#token=");
    assertThat(payload.path("timezone").asText()).isEqualTo("Asia/Kolkata");
  }

  @Test
  void anExistingEmailIsTaken() throws Exception {
    String email = uniqueEmail("cust");
    registration.register(new RegisterRequest(email, PASSWORD, "Cust", "UTC"));

    invite(adminToken, email.toUpperCase(), "Someone", "Someone", "UTC")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void onlyAdminsMayInvite() throws Exception {
    String customer = uniqueEmail("cust");
    registration.register(new RegisterRequest(customer, PASSWORD, "Cust", "UTC"));
    String provider = uniqueEmail("prov");
    insertUser(provider, "PROVIDER", passwordEncoder.encode(PASSWORD));

    invite(accessToken(customer), uniqueEmail("x"), "X", "X", "UTC")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    invite(accessToken(provider), uniqueEmail("y"), "Y", "Y", "UTC")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void invitingRequiresAToken() throws Exception {
    mvc.perform(
            post("/admin/providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(uniqueEmail("z"), "Z", "Z", "UTC")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anInvitedProviderCannotLogInYet() throws Exception {
    String email = uniqueEmail("dr");
    invite(adminToken, email, "Dr X", "Dr X", "UTC").andExpect(status().isCreated());

    login(email, PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void theListShowsInvitePendingUntilAccepted() throws Exception {
    String pending = uniqueEmail("pending");
    String accepted = uniqueEmail("accepted");
    // Display names that sort first, so the shared test database can't push them off page 0.
    invite(adminToken, pending, "Pending", "000 Pending " + UUID.randomUUID(), "UTC")
        .andExpect(status().isCreated());
    invite(adminToken, accepted, "Accepted", "000 Accepted " + UUID.randomUUID(), "UTC")
        .andExpect(status().isCreated());
    // Accepting arrives in #28; simulate it by setting a password.
    jdbc.update(
        "UPDATE users SET password_hash = ? WHERE email = ?",
        passwordEncoder.encode(PASSWORD),
        accepted);

    String response =
        mvc.perform(
                get("/admin/providers")
                    .param("size", "100")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(100))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode list = json.readTree(response);
    assertThat(list.path("total").asLong())
        .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM providers", Long.class));
    assertThat(list.path("items").get(0).path("displayName").asText()).startsWith("000 Accepted");
    assertThat(statusOf(list, pending)).isTrue();
    assertThat(statusOf(list, accepted)).isFalse();
  }

  @Test
  void theDatabaseCatchesADuplicateThePreCheckMissed() throws Exception {
    String email = uniqueEmail("race");
    registration.register(new RegisterRequest(email, PASSWORD, "First", "UTC"));
    doReturn(false).when(users).emailExists(anyString());

    invite(adminToken, email, "Second", "Second", "UTC")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void aHugePageNumberIsAValidationErrorNotA500() throws Exception {
    mvc.perform(
            get("/admin/providers")
                .param("page", "100000000")
                .param("size", "100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("page")));
  }

  @Test
  void pageSizeIsCappedAt100() throws Exception {
    mvc.perform(
            get("/admin/providers")
                .param("size", "101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("size")));
  }

  @Test
  void invalidTimezoneAndBlankNameAreValidationErrors() throws Exception {
    invite(adminToken, uniqueEmail("bad"), " ", " ", "Mars/Olympus")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("timezone")))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("displayName")))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("fullName")));
  }

  @Test
  void aFailedEmailWriteRollsBackTheWholeInvite() throws Exception {
    String email = uniqueEmail("atomic");
    doThrow(new IllegalStateException("outbox unavailable")).when(outbox).save(any());

    invite(adminToken, email, "Atomic", "Atomic", "UTC")
        .andExpect(status().isInternalServerError());

    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
        .isZero();
  }

  private Boolean statusOf(JsonNode list, String email) {
    for (JsonNode item : list.path("items")) {
      if (item.path("email").asText().equals(email)) {
        return item.path("invitePending").asBoolean();
      }
    }
    throw new AssertionError(email + " is not in the list");
  }

  private ResultActions invite(
      String token, String email, String fullName, String displayName, String timezone)
      throws Exception {
    return mvc.perform(
        post("/admin/providers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(email, fullName, displayName, timezone)));
  }

  private String body(String email, String fullName, String displayName, String timezone)
      throws Exception {
    return json.writeValueAsString(
        Map.of(
            "email",
            email,
            "fullName",
            fullName,
            "displayName",
            displayName,
            "timezone",
            timezone));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mvc.perform(
        post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email, "password", password))));
  }

  private String accessToken(String email) throws Exception {
    String response =
        login(email, PASSWORD)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).path("accessToken").asText();
  }

  private void insertUser(String email, String role, String passwordHash) {
    jdbc.update(
        "INSERT INTO users (id, email, full_name, role, password_hash) VALUES (?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        email,
        "Test " + role.toLowerCase(),
        role,
        passwordHash);
  }

  private static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@example.com";
  }
}
