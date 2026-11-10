package com.appointmentprep.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Providers choose the services they offer (P3; TECHNICAL_DESIGN §9.1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProviderServicesTest {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper json;

  private Provider me;

  /** A logged-in provider: their user id, provider id and access token. */
  private record Provider(UUID userId, UUID providerId, String token) {}

  @BeforeEach
  void logInAsProvider() throws Exception {
    me = newProvider();
  }

  @Test
  void putReplacesTheSet() throws Exception {
    String a = service("Consultation", true);
    String b = service("Follow-up", true);

    replace(me, a, b)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offeredServiceIds", containsInAnyOrder(a, b)));
    offered(me).andExpect(jsonPath("$.offeredServiceIds", containsInAnyOrder(a, b)));

    replace(me, b).andExpect(status().isOk());
    offered(me).andExpect(jsonPath("$.offeredServiceIds", containsInAnyOrder(b)));
  }

  @Test
  void anEmptyListOffersNothing() throws Exception {
    replace(me, service("Consultation", true)).andExpect(status().isOk());

    replace(me).andExpect(status().isOk()).andExpect(jsonPath("$.offeredServiceIds").isEmpty());
    assertThat(linkCount(me)).isZero();
  }

  @Test
  void unknownOrInactiveIdIsRejectedAndNothingChanges() throws Exception {
    String a = service("Consultation", true);
    replace(me, a).andExpect(status().isOk());

    for (String bad : new String[] {UUID.randomUUID().toString(), service("Retired", false)}) {
      replace(me, service("New one", true), bad)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.errors[*].field").value(hasItem("serviceIds")));
      offered(me).andExpect(jsonPath("$.offeredServiceIds", containsInAnyOrder(a)));
    }
  }

  @Test
  void missingOrNullIdsAreAValidationError() throws Exception {
    send(me, "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    send(me, "{\"serviceIds\":[null]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void duplicatesAreStoredOnce() throws Exception {
    String a = service("Consultation", true);

    replace(me, a, a, a)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offeredServiceIds", containsInAnyOrder(a)));
    assertThat(linkCount(me)).isEqualTo(1);
  }

  @Test
  void providersAreKeptApart() throws Exception {
    Provider other = newProvider();
    String a = service("Consultation", true);
    String b = service("Follow-up", true);
    replace(other, a).andExpect(status().isOk());

    replace(me, b).andExpect(status().isOk());
    replace(me).andExpect(status().isOk());

    offered(other).andExpect(jsonPath("$.offeredServiceIds", containsInAnyOrder(a)));
  }

  @Test
  void getListsOnlyActiveServicesToChooseFrom() throws Exception {
    String active = service("Consultation", true);
    String inactive = service("Retired", false);

    offered(me)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available[*].id").value(hasItem(active)))
        .andExpect(jsonPath("$.available[*].id").value(not(hasItem(inactive))))
        .andExpect(
            jsonPath("$.available[?(@.id == '" + active + "')].durationMinutes")
                .value(hasItem(30)));
  }

  @Test
  void aLinkToADeactivatedServiceIsKeptSoReactivatingRestoresIt() throws Exception {
    String a = service("Consultation", true);
    String b = service("Follow-up", true);
    replace(me, a, b).andExpect(status().isOk());
    jdbc.update("UPDATE services SET active = false WHERE id = ?", UUID.fromString(a));

    // Hidden while inactive, and a save of the active set doesn't drop it.
    offered(me).andExpect(jsonPath("$.offeredServiceIds", containsInAnyOrder(b)));
    replace(me, b).andExpect(status().isOk());

    jdbc.update("UPDATE services SET active = true WHERE id = ?", UUID.fromString(a));
    offered(me).andExpect(jsonPath("$.offeredServiceIds", containsInAnyOrder(a, b)));
  }

  @Test
  void aDeactivatedProviderCannotChangeTheirServices() throws Exception {
    jdbc.update("UPDATE providers SET active = false WHERE id = ?", me.providerId());

    replace(me, service("Consultation", true)).andExpect(status().isNotFound());
  }

  @Test
  void onlyProvidersManageTheirServices() throws Exception {
    for (String role : new String[] {"CUSTOMER", "ADMIN"}) {
      String token = login(insertUser(UUID.randomUUID(), role));
      mvc.perform(get("/provider/services").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isForbidden());
      mvc.perform(
              put("/provider/services")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"serviceIds\":[]}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/provider/services")).andExpect(status().isUnauthorized());
    mvc.perform(
            put("/provider/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceIds\":[]}"))
        .andExpect(status().isUnauthorized());
  }

  private ResultActions offered(Provider provider) throws Exception {
    return mvc.perform(
        get("/provider/services").header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token()));
  }

  private ResultActions replace(Provider provider, String... ids) throws Exception {
    List<String> list = new ArrayList<>(Arrays.asList(ids));
    return send(provider, json.writeValueAsString(Map.of("serviceIds", list)));
  }

  private ResultActions send(Provider provider, String body) throws Exception {
    return mvc.perform(
        put("/provider/services")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private int linkCount(Provider provider) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM provider_services WHERE provider_id = ?",
        Integer.class,
        provider.providerId());
  }

  private String service(String name, boolean active) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO services (id, name, duration_minutes, active) VALUES (?, ?, 30, ?)",
        id,
        name + " " + id.toString().substring(0, 8),
        active);
    return id.toString();
  }

  private Provider newProvider() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();
    String email = insertUser(userId, "PROVIDER");
    jdbc.update(
        "INSERT INTO providers (id, user_id, display_name, timezone) VALUES (?, ?, 'Dr Test', 'UTC')",
        providerId,
        userId);
    return new Provider(userId, providerId, login(email));
  }

  private String insertUser(UUID id, String role) {
    String email = role.toLowerCase() + "-" + id + "@example.com";
    jdbc.update(
        "INSERT INTO users (id, email, full_name, role, password_hash) VALUES (?, ?, ?, ?, ?)",
        id,
        email,
        "Test " + role.toLowerCase(),
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
