package com.appointmentprep.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.appointmentprep.auth.RegisterRequest;
import com.appointmentprep.auth.RegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** PATCH /api/me (TECHNICAL_DESIGN §9.1, §10.3). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProfileTest {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private RegistrationService registration;

  @Autowired private ObjectMapper json;

  @Test
  void updatesNameAndTimezone() throws Exception {
    String email = registerCustomer();
    String token = accessToken(email);

    patchMe(token, "{\"fullName\": \"Asha R. Rao\", \"timezone\": \"Europe/London\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Asha R. Rao"))
        .andExpect(jsonPath("$.timezone").value("Europe/London"))
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("CUSTOMER"));

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT full_name, timezone, role, email FROM users WHERE email = ?", email);
    assertThat(row)
        .containsEntry("full_name", "Asha R. Rao")
        .containsEntry("timezone", "Europe/London")
        .containsEntry("role", "CUSTOMER");
  }

  @Test
  void aPartialUpdateLeavesOtherFieldsAlone() throws Exception {
    String email = registerCustomer();

    patchMe(accessToken(email), "{\"timezone\": \"America/New_York\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("America/New_York"))
        .andExpect(jsonPath("$.fullName").value("Asha"));
  }

  @Test
  void emailAndRoleCannotBeChangedHere() throws Exception {
    String email = registerCustomer();

    patchMe(accessToken(email), "{\"email\": \"evil@example.com\", \"role\": \"ADMIN\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("CUSTOMER"));
  }

  @Test
  void invalidTimezoneIsAValidationError() throws Exception {
    patchMe(accessToken(registerCustomer()), "{\"timezone\": \"Mars/Olympus\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("timezone")));
  }

  @Test
  void blankNameIsAValidationError() throws Exception {
    patchMe(accessToken(registerCustomer()), "{\"fullName\": \"   \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("fullName")));
  }

  @Test
  void requiresAToken() throws Exception {
    mvc.perform(
            patch("/me").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\": \"X\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  private ResultActions patchMe(String token, String body) throws Exception {
    return mvc.perform(
        patch("/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private String registerCustomer() {
    String email = "user-" + UUID.randomUUID() + "@example.com";
    registration.register(new RegisterRequest(email, PASSWORD, "Asha", "Asia/Kolkata"));
    return email;
  }

  private String accessToken(String email) throws Exception {
    String body = json.writeValueAsString(Map.of("email", email, "password", PASSWORD));
    String response =
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).path("accessToken").asText();
  }
}
