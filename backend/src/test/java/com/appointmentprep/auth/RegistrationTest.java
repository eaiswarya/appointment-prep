package com.appointmentprep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** POST /api/auth/register (FR-1, C1; TECHNICAL_DESIGN §8.1, §9.1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegistrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void registersACustomer() throws Exception {
    String email = uniqueEmail();

    register(email, "correct-horse-battery", "Asha Rao", "Asia/Kolkata")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.fullName").value("Asha Rao"))
        .andExpect(jsonPath("$.role").value("CUSTOMER"))
        .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT role, password_hash, timezone, active FROM users WHERE email = ?", email);
    assertThat(row.get("role")).isEqualTo("CUSTOMER");
    assertThat((String) row.get("password_hash")).startsWith("$2a$12$");
    assertThat(row.get("active")).isEqualTo(true);
  }

  @Test
  void duplicateEmailIgnoringCaseIsEmailTaken() throws Exception {
    String email = uniqueEmail();
    register(email, "correct-horse-battery", "First", "UTC").andExpect(status().isCreated());

    register(email.toUpperCase(), "another-long-secret", "Second", "UTC")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void shortPasswordIsRejected() throws Exception {
    register(uniqueEmail(), "short12", "Asha", "UTC")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
  }

  @Test
  void commonPasswordIsRejected() throws Exception {
    register(uniqueEmail(), "password1", "Asha", "UTC")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
  }

  @Test
  void invalidTimezoneIsRejected() throws Exception {
    register(uniqueEmail(), "correct-horse-battery", "Asha", "Mars/Olympus")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("timezone")));
  }

  @Test
  void invalidEmailAndBlankNameAreRejected() throws Exception {
    register("not-an-email", "correct-horse-battery", " ", "UTC")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
        .andExpect(jsonPath("$.errors[*].field").value(hasItem("fullName")));
  }

  private ResultActions register(String email, String password, String fullName, String timezone)
      throws Exception {
    String body =
        """
        {"email": "%s", "password": "%s", "fullName": "%s", "timezone": "%s"}
        """
            .formatted(email, password, fullName, timezone);
    return mvc.perform(
        post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }
}
