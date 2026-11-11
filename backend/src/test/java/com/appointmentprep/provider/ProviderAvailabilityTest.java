package com.appointmentprep.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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

/** Weekly working hours (P1, FR-8; TECHNICAL_DESIGN §5.2, §6.1, §9.1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProviderAvailabilityTest {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper json;

  private LoggedInProvider me;

  private record LoggedInProvider(UUID providerId, String token) {}

  @BeforeEach
  void logInAsProvider() throws Exception {
    me = newProvider("Asia/Kolkata");
  }

  @Test
  void putStoresTheRulesAndGetReturnsThemSorted() throws Exception {
    // Sent out of order on purpose.
    List<Map<String, Object>> weekdays = new ArrayList<>();
    for (int day : new int[] {5, 3, 1, 4, 2}) {
      weekdays.add(rule(day, "09:00", "17:00"));
    }

    replace(me, weekdays)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
        .andExpect(jsonPath("$.rules.length()").value(5))
        .andExpect(jsonPath("$.rules[0].dayOfWeek").value(1))
        .andExpect(jsonPath("$.rules[4].dayOfWeek").value(5))
        .andExpect(jsonPath("$.rules[0].startTime").value("09:00"))
        .andExpect(jsonPath("$.rules[0].endTime").value("17:00"));

    rules(me)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rules[*].dayOfWeek").value(contains(1, 2, 3, 4, 5)));
  }

  @Test
  void rulesAreStoredAsTheProvidersLocalTimesNeverConvertedToUtc() throws Exception {
    replace(me, List.of(rule(1, "09:00", "17:00"))).andExpect(status().isOk());

    // Asia/Kolkata is UTC+05:30; a conversion would have stored 03:30.
    assertThat(
            jdbc.queryForObject(
                "SELECT start_time::text FROM availability_rules WHERE provider_id = ?",
                String.class,
                me.providerId()))
        .isEqualTo("09:00:00");
  }

  @Test
  void sameDayRangesAreSortedByStartTime() throws Exception {
    replace(me, List.of(rule(2, "14:00", "18:00"), rule(2, "08:00", "12:00")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rules[*].startTime").value(contains("08:00", "14:00")));
  }

  @Test
  void aLaterPutReplacesEverythingAndEmptyClears() throws Exception {
    replace(me, List.of(rule(1, "09:00", "17:00"), rule(2, "09:00", "17:00")))
        .andExpect(status().isOk());

    replace(me, List.of(rule(6, "10:00", "14:00"))).andExpect(status().isOk());
    rules(me).andExpect(jsonPath("$.rules[*].dayOfWeek").value(contains(6)));

    replace(me, List.of()).andExpect(status().isOk()).andExpect(jsonPath("$.rules").isEmpty());
    assertThat(ruleCount(me)).isZero();
  }

  @Test
  void overlappingRangesAreRejectedAndNothingChanges() throws Exception {
    replace(me, List.of(rule(1, "09:00", "17:00"))).andExpect(status().isOk());

    replace(me, List.of(rule(3, "09:00", "12:00"), rule(3, "11:00", "14:00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("rules[1]")));

    rules(me).andExpect(jsonPath("$.rules[*].dayOfWeek").value(contains(1)));
  }

  @Test
  void aRangeInsideAnotherOverlapsToo() throws Exception {
    replace(me, List.of(rule(4, "09:00", "17:00"), rule(4, "10:00", "11:00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("rules[1]")));
  }

  @Test
  void backToBackRangesAreAccepted() throws Exception {
    replace(me, List.of(rule(1, "09:00", "12:00"), rule(1, "12:00", "17:00")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rules.length()").value(2));
  }

  @Test
  void theSameHoursOnDifferentDaysDoNotOverlap() throws Exception {
    replace(me, List.of(rule(1, "09:00", "17:00"), rule(2, "09:00", "17:00")))
        .andExpect(status().isOk());
  }

  @Test
  void invalidRuleFieldsAreValidationErrors() throws Exception {
    expectFieldError(rule(1, "17:00", "09:00"), "rules[0].endTime");
    expectFieldError(rule(1, "09:00", "09:00"), "rules[0].endTime");
    expectFieldError(rule(1, "09:07", "17:00"), "rules[0].startTime");
    expectFieldError(rule(1, "09:00", "16:58"), "rules[0].endTime");
    expectFieldError(rule(0, "09:00", "17:00"), "rules[0].dayOfWeek");
    expectFieldError(rule(8, "09:00", "17:00"), "rules[0].dayOfWeek");
    assertThat(ruleCount(me)).isZero();
  }

  @Test
  void malformedTimesAndMissingFieldsAreValidationErrors() throws Exception {
    send(me, "{\"rules\":[{\"dayOfWeek\":1,\"startTime\":\"9am\",\"endTime\":\"17:00\"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("rules[0].startTime")));
    send(me, "{\"rules\":[{\"dayOfWeek\":1,\"startTime\":\"09:00\"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("rules[0].endTime")));
    send(me, "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("rules")));
  }

  @Test
  void providersAreKeptApart() throws Exception {
    LoggedInProvider other = newProvider("Europe/London");
    replace(other, List.of(rule(2, "08:00", "12:00"))).andExpect(status().isOk());

    replace(me, List.of(rule(1, "09:00", "17:00"))).andExpect(status().isOk());
    replace(me, List.of()).andExpect(status().isOk());

    rules(other)
        .andExpect(jsonPath("$.timezone").value("Europe/London"))
        .andExpect(jsonPath("$.rules[*].dayOfWeek").value(contains(2)));
  }

  @Test
  void aDeactivatedProviderCanReadButNotChangeTheirHours() throws Exception {
    jdbc.update("UPDATE providers SET active = false WHERE id = ?", me.providerId());

    replace(me, List.of(rule(1, "09:00", "17:00"))).andExpect(status().isNotFound());
    rules(me).andExpect(status().isOk());
  }

  @Test
  void onlyProvidersManageTheirHours() throws Exception {
    for (String role : new String[] {"CUSTOMER", "ADMIN"}) {
      String token = login(insertUser(UUID.randomUUID(), role));
      mvc.perform(
              get("/provider/availability").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isForbidden());
      mvc.perform(
              put("/provider/availability")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rules\":[]}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/provider/availability")).andExpect(status().isUnauthorized());
    mvc.perform(
            put("/provider/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[]}"))
        .andExpect(status().isUnauthorized());
  }

  private void expectFieldError(Map<String, Object> rule, String field) throws Exception {
    replace(me, List.of(rule))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem(field)));
  }

  private static Map<String, Object> rule(int day, String start, String end) {
    return Map.of("dayOfWeek", day, "startTime", start, "endTime", end);
  }

  private ResultActions rules(LoggedInProvider provider) throws Exception {
    return mvc.perform(
        get("/provider/availability")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token()));
  }

  private ResultActions replace(LoggedInProvider provider, List<Map<String, Object>> rules)
      throws Exception {
    return send(provider, json.writeValueAsString(Map.of("rules", rules)));
  }

  private ResultActions send(LoggedInProvider provider, String body) throws Exception {
    return mvc.perform(
        put("/provider/availability")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private int ruleCount(LoggedInProvider provider) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM availability_rules WHERE provider_id = ?",
        Integer.class,
        provider.providerId());
  }

  private LoggedInProvider newProvider(String timezone) throws Exception {
    UUID userId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();
    String email = insertUser(userId, "PROVIDER");
    jdbc.update(
        "INSERT INTO providers (id, user_id, display_name, timezone) VALUES (?, ?, 'Dr Test', ?)",
        providerId,
        userId,
        timezone);
    return new LoggedInProvider(providerId, login(email));
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
