package com.appointmentprep.provider;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** A provider's own profile: display name, bio, time zone, buffer (TECHNICAL_DESIGN §9.1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProviderProfileTest {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper json;

  private String providerToken;

  @BeforeEach
  void logInAsProvider() throws Exception {
    providerToken = providerToken("Dr Iyer", "Asia/Kolkata");
  }

  @Test
  void providerReadsTheirProfile() throws Exception {
    mvc.perform(get("/provider/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Dr Iyer"))
        .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
        .andExpect(jsonPath("$.bufferMinutes").value(0))
        .andExpect(jsonPath("$.bio").doesNotExist());
  }

  @Test
  void patchSavesTheGivenFields() throws Exception {
    update(
            Map.of(
                "displayName", "Dr Meera Iyer",
                "bio", "Family medicine.",
                "timezone", "Europe/London",
                "bufferMinutes", 15))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Dr Meera Iyer"))
        .andExpect(jsonPath("$.bufferMinutes").value(15));

    mvc.perform(get("/provider/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
        .andExpect(jsonPath("$.displayName").value("Dr Meera Iyer"))
        .andExpect(jsonPath("$.bio").value("Family medicine."))
        .andExpect(jsonPath("$.timezone").value("Europe/London"))
        .andExpect(jsonPath("$.bufferMinutes").value(15));
  }

  @Test
  void patchLeavesOtherFieldsAloneAndAnEmptyBioClearsIt() throws Exception {
    update(Map.of("bio", "Family medicine.")).andExpect(status().isOk());

    update(Map.of("bufferMinutes", 120))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Dr Iyer"))
        .andExpect(jsonPath("$.bio").value("Family medicine."))
        .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
        .andExpect(jsonPath("$.bufferMinutes").value(120));

    update(Map.of("bio", "  "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bio").doesNotExist());
  }

  @ParameterizedTest
  @ValueSource(ints = {-5, 125, 7})
  void invalidBufferIsAValidationError(int minutes) throws Exception {
    update(Map.of("bufferMinutes", minutes))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("bufferMinutes")));
  }

  @Test
  void invalidTimezoneIsAValidationError() throws Exception {
    update(Map.of("timezone", "Mars/Olympus_Mons"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("timezone")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "　", "DrIyer", "x"})
  void invalidDisplayNameIsAValidationError(String name) throws Exception {
    String value = name.equals("x") ? "x".repeat(121) : name;

    update(Map.of("displayName", value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("displayName")));
  }

  @Test
  void anEmptyPatchChangesNothing() throws Exception {
    update(Map.of())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Dr Iyer"))
        .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
        .andExpect(jsonPath("$.bufferMinutes").value(0));
  }

  @Test
  void aProviderOnlyEverChangesTheirOwnProfile() throws Exception {
    String mine = providerToken;
    String other = providerToken("Dr Shah", "Europe/London");

    mvc.perform(
            patch("/provider/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + mine)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Dr Meera Iyer\",\"bufferMinutes\":30}"))
        .andExpect(status().isOk());

    mvc.perform(get("/provider/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
        .andExpect(jsonPath("$.displayName").value("Dr Shah"))
        .andExpect(jsonPath("$.timezone").value("Europe/London"))
        .andExpect(jsonPath("$.bufferMinutes").value(0));
  }

  @Test
  void onlyProvidersHaveAProviderProfile() throws Exception {
    for (String role : new String[] {"CUSTOMER", "ADMIN"}) {
      String token = tokenFor(role);
      mvc.perform(get("/provider/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isForbidden());
      mvc.perform(
              patch("/provider/me")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"bufferMinutes\":10}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/provider/me")).andExpect(status().isUnauthorized());
    mvc.perform(patch("/provider/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aDeactivatedProviderCannotChangeTheirProfile() throws Exception {
    // A deactivated provider may still hold a 15-minute access token (§8.1).
    jdbc.update("UPDATE providers SET active = false WHERE user_id = ?", providerUserId);

    update(Map.of("bufferMinutes", 30)).andExpect(status().isNotFound());
    // Reading stays allowed: the profile is shown read-only rather than vanishing.
    mvc.perform(get("/provider/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bufferMinutes").value(0));
  }

  private UUID providerUserId;

  private ResultActions update(Map<String, Object> body) throws Exception {
    return mvc.perform(
        patch("/provider/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new HashMap<>(body))));
  }

  private String providerToken(String displayName, String timezone) throws Exception {
    UUID userId = UUID.randomUUID();
    providerUserId = userId;
    String email = insertUser(userId, "PROVIDER", displayName);
    jdbc.update(
        "INSERT INTO providers (id, user_id, display_name, timezone) VALUES (?, ?, ?, ?)",
        UUID.randomUUID(),
        userId,
        displayName,
        timezone);
    return login(email);
  }

  private String tokenFor(String role) throws Exception {
    return login(insertUser(UUID.randomUUID(), role, "Test " + role.toLowerCase()));
  }

  private String insertUser(UUID id, String role, String fullName) {
    String email = role.toLowerCase() + "-" + id + "@example.com";
    jdbc.update(
        "INSERT INTO users (id, email, full_name, role, password_hash) VALUES (?, ?, ?, ?, ?)",
        id,
        email,
        fullName,
        role,
        passwordEncoder.encode(PASSWORD));
    return email;
  }

  private String login(String email) throws Exception {
    String response =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).path("accessToken").asText();
  }
}
