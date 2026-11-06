package com.appointmentprep.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointmentprep.TestcontainersConfiguration;
import com.appointmentprep.common.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Per-IP rate limits on login and forgot-password (TECHNICAL_DESIGN §8.3). */
@SpringBootTest(properties = "app.rate-limit.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthRateLimitTest {

  private static final Instant T0 = Instant.parse("2026-11-03T04:30:00Z");
  private static final MutableClock CLOCK = new MutableClock(T0);
  private static final String PASSWORD = "correct-horse-battery";
  // Buckets live as long as the app context, so every test uses addresses of its own.
  private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

  @TestBean private Clock clock;

  @Autowired private MockMvc mvc;

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
  void theSixthLoginFromOneIpWithinAMinuteIsRateLimited() throws Exception {
    String ip = newIp();
    for (int i = 0; i < 5; i++) {
      login(ip, "nobody@example.com", "wrong-password-123").andExpect(status().isUnauthorized());
    }
    CLOCK.advance(Duration.ofSeconds(20));

    login(ip, "nobody@example.com", "wrong-password-123")
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "40"));
  }

  @Test
  void theFourthForgotPasswordFromOneIpWithinAnHourIsRateLimited() throws Exception {
    String ip = newIp();
    for (int i = 0; i < 3; i++) {
      forgotPassword(ip).andExpect(status().isAccepted());
    }

    forgotPassword(ip)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "3600"));
  }

  @Test
  void anotherIpCanStillLogInWhileOneIsLimited() throws Exception {
    String email = registerCustomer();
    String limited = newIp();
    for (int i = 0; i < 6; i++) {
      login(limited, email, "wrong-password-123");
    }
    login(limited, email, PASSWORD).andExpect(status().isTooManyRequests());

    login(newIp(), email, PASSWORD).andExpect(status().isOk());
  }

  @Test
  void theLimitsAreSeparate() throws Exception {
    String ip = newIp();
    for (int i = 0; i < 3; i++) {
      forgotPassword(ip).andExpect(status().isAccepted());
    }
    forgotPassword(ip).andExpect(status().isTooManyRequests());

    login(ip, registerCustomer(), PASSWORD).andExpect(status().isOk());
  }

  @Test
  void loginIsAllowedAgainOnceTheMinuteHasPassed() throws Exception {
    String email = registerCustomer();
    String ip = newIp();
    for (int i = 0; i < 5; i++) {
      login(ip, email, "wrong-password-123").andExpect(status().isUnauthorized());
    }
    login(ip, email, PASSWORD).andExpect(status().isTooManyRequests());

    CLOCK.advance(Duration.ofMinutes(1));

    login(ip, email, PASSWORD).andExpect(status().isOk());
  }

  @Test
  void forgotPasswordIsAllowedAgainOnceTheHourHasPassed() throws Exception {
    String ip = newIp();
    for (int i = 0; i < 3; i++) {
      forgotPassword(ip).andExpect(status().isAccepted());
    }
    forgotPassword(ip).andExpect(status().isTooManyRequests());

    CLOCK.advance(Duration.ofMinutes(59));
    forgotPassword(ip).andExpect(status().isTooManyRequests());
    CLOCK.advance(Duration.ofMinutes(1));

    forgotPassword(ip).andExpect(status().isAccepted());
  }

  @Test
  void aSpoofedForwardedForEntryDoesNotEscapeTheLimit() throws Exception {
    // Nginx appends the real client address, so only the last entry is trusted.
    String ip = newIp();
    for (int i = 0; i < 5; i++) {
      login(newIp() + ", " + ip, "nobody@example.com", "wrong-password-123")
          .andExpect(status().isUnauthorized());
    }

    login(newIp() + ", " + ip, "nobody@example.com", "wrong-password-123")
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void withoutForwardedForTheConnectionAddressIsUsed() throws Exception {
    String ip = newIp();
    for (int i = 0; i < 5; i++) {
      loginFromAddress(ip).andExpect(status().isUnauthorized());
    }

    loginFromAddress(ip).andExpect(status().isTooManyRequests());
    login(newIp(), "nobody@example.com", "wrong-password-123").andExpect(status().isUnauthorized());
  }

  private static String newIp() {
    int n = NEXT_IP.getAndIncrement();
    return "198.51." + (n / 250) + "." + (n % 250 + 1);
  }

  private String registerCustomer() {
    String email = "user-" + UUID.randomUUID() + "@example.com";
    registration.register(new RegisterRequest(email, PASSWORD, "Rate Tester", "UTC"));
    return email;
  }

  private ResultActions login(String forwardedFor, String email, String password) throws Exception {
    return mvc.perform(
        post("/auth/login")
            .header("X-Forwarded-For", forwardedFor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email, "password", password))));
  }

  private ResultActions loginFromAddress(String remoteAddr) throws Exception {
    return mvc.perform(
        post("/auth/login")
            .with(
                request -> {
                  request.setRemoteAddr(remoteAddr);
                  return request;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("email", "nobody@example.com", "password", "wrong-password-123"))));
  }

  private ResultActions forgotPassword(String forwardedFor) throws Exception {
    return mvc.perform(
        post("/auth/forgot-password")
            .header("X-Forwarded-For", forwardedFor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", "someone@example.com"))));
  }
}
