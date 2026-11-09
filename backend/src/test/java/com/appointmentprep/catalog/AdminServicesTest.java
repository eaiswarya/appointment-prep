package com.appointmentprep.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The service catalog (A2; TECHNICAL_DESIGN §9.1, §9.3). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminServicesTest {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper json;

  private String adminToken;

  @BeforeEach
  void logInAsAdmin() throws Exception {
    adminToken = tokenFor("ADMIN");
  }

  @Test
  void createdServiceIsListedPublicly() throws Exception {
    String name = uniqueName("Initial consultation");

    create(Map.of("name", name, "description", "First visit", "durationMinutes", 30))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.name").value(name))
        .andExpect(jsonPath("$.description").value("First visit"))
        .andExpect(jsonPath("$.durationMinutes").value(30))
        .andExpect(jsonPath("$.active").value(true));

    // Anonymous: no Authorization header.
    mvc.perform(get("/services"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name").value(hasItem(name)));
  }

  @ParameterizedTest
  @ValueSource(ints = {7, 0, 485, -5})
  void invalidDurationIsRejected(int minutes) throws Exception {
    String name = uniqueName("Odd length");

    create(Map.of("name", name, "durationMinutes", minutes))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DURATION"));

    assertThat(countNamed(name)).isZero();
  }

  @Test
  void missingDurationIsAValidationError() throws Exception {
    create(Map.of("name", uniqueName("No length")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("durationMinutes")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "x"})
  void invalidNameIsAValidationError(String name) throws Exception {
    String value = name.equals("x") ? "x".repeat(121) : name;

    create(Map.of("name", value, "durationMinutes", 30))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("name")));
  }

  @Test
  void deactivatedServiceIsHiddenPubliclyButKept() throws Exception {
    String name = uniqueName("Follow-up");
    String id = createdId(name, 15);
    UUID providerId = insertProviderOffering(id);

    update(id, Map.of("active", false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mvc.perform(get("/services")).andExpect(jsonPath("$[*].id").value(not(hasItem(id))));
    adminList()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.id == '" + id + "')].active").value(hasItem(false)));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM provider_services WHERE provider_id = ? AND service_id = ?",
                Integer.class,
                providerId,
                UUID.fromString(id)))
        .isEqualTo(1);

    update(id, Map.of("active", true)).andExpect(status().isOk());
    mvc.perform(get("/services")).andExpect(jsonPath("$[*].id").value(hasItem(id)));
  }

  @Test
  void patchUpdatesOnlyTheGivenFields() throws Exception {
    String id = createdId(uniqueName("Check-up"), 30);
    String renamed = uniqueName("Annual check-up");

    update(id, Map.of("name", renamed, "description", "Once a year", "durationMinutes", 45))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(renamed))
        .andExpect(jsonPath("$.description").value("Once a year"))
        .andExpect(jsonPath("$.durationMinutes").value(45))
        .andExpect(jsonPath("$.active").value(true));

    update(id, Map.of("durationMinutes", 60))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(renamed))
        .andExpect(jsonPath("$.description").value("Once a year"))
        .andExpect(jsonPath("$.durationMinutes").value(60));

    // An empty description clears it.
    update(id, Map.of("description", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").doesNotExist());
  }

  @Test
  void patchChecksTheDurationAndNameToo() throws Exception {
    String id = createdId(uniqueName("Short call"), 10);

    update(id, Map.of("durationMinutes", 12))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DURATION"));
    update(id, Map.of("name", " "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("name")));

    adminList()
        .andExpect(jsonPath("$.items[?(@.id == '" + id + "')].durationMinutes").value(hasItem(10)));
  }

  @Test
  void unknownServiceIsNotFound() throws Exception {
    update(UUID.randomUUID().toString(), Map.of("active", false))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void onlyAdminsManageServices() throws Exception {
    String id = createdId(uniqueName("Admin only"), 30);
    for (String role : new String[] {"CUSTOMER", "PROVIDER"}) {
      String token = tokenFor(role);
      mvc.perform(withToken(get("/admin/services"), token)).andExpect(status().isForbidden());
      mvc.perform(
              withToken(post("/admin/services"), token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(Map.of("name", "X", "durationMinutes", 30))))
          .andExpect(status().isForbidden());
      mvc.perform(
              withToken(patch("/admin/services/" + id), token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"active\":false}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/admin/services")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminListIsPaginatedAndSortedByName() throws Exception {
    String prefix = "zz-sort-" + UUID.randomUUID();
    createdId(prefix + " b", 30);
    createdId(prefix + " a", 30);

    JsonNode page =
        json.readTree(
            mvc.perform(
                    withToken(get("/admin/services"), adminToken)
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.total").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String previous = "";
    for (JsonNode item : page.path("items")) {
      String name = item.path("name").asText().toLowerCase();
      assertThat(name).isGreaterThanOrEqualTo(previous);
      previous = name;
    }
    mvc.perform(withToken(get("/admin/services"), adminToken).param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  private ResultActions create(Map<String, Object> body) throws Exception {
    return mvc.perform(
        withToken(post("/admin/services"), adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private String createdId(String name, int minutes) throws Exception {
    String response =
        create(Map.of("name", name, "durationMinutes", minutes))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).path("id").asText();
  }

  private ResultActions update(String id, Map<String, Object> body) throws Exception {
    return mvc.perform(
        withToken(patch("/admin/services/" + id), adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new HashMap<>(body))));
  }

  private ResultActions adminList() throws Exception {
    return mvc.perform(withToken(get("/admin/services"), adminToken).param("size", "100"));
  }

  private static MockHttpServletRequestBuilder withToken(
      MockHttpServletRequestBuilder request, String token) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private int countNamed(String name) {
    return jdbc.queryForObject("SELECT count(*) FROM services WHERE name = ?", Integer.class, name);
  }

  private UUID insertProviderOffering(String serviceId) {
    UUID userId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, full_name, role) VALUES (?, ?, 'Dr Test', 'PROVIDER')",
        userId,
        "dr-" + userId + "@example.com");
    jdbc.update(
        "INSERT INTO providers (id, user_id, display_name, timezone) VALUES (?, ?, 'Dr Test', 'UTC')",
        providerId,
        userId);
    jdbc.update(
        "INSERT INTO provider_services (provider_id, service_id) VALUES (?, ?)",
        providerId,
        UUID.fromString(serviceId));
    return providerId;
  }

  private String tokenFor(String role) throws Exception {
    String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
    jdbc.update(
        "INSERT INTO users (id, email, full_name, role, password_hash) VALUES (?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        email,
        "Test " + role.toLowerCase(),
        role,
        passwordEncoder.encode(PASSWORD));
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

  private static String uniqueName(String base) {
    return base + " " + UUID.randomUUID().toString().substring(0, 8);
  }
}
