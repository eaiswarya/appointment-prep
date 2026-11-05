package com.appointmentprep.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** RFC 7807 errors with a §9.3 code and the request ID (TECHNICAL_DESIGN §9.3, §13). */
// Error format only: the security filter chain is covered by LoginTest against the full app.
@WebMvcTest(
    controllers = ErrorProbeController.class,
    excludeAutoConfiguration = {
      SecurityAutoConfiguration.class,
      OAuth2ResourceServerAutoConfiguration.class
    })
@ActiveProfiles("error-probe")
class ErrorHandlingTest {

  @Autowired private MockMvc mvc;

  @Test
  void validationFailureListsEachBadField() throws Exception {
    mvc.perform(
            post("/probe/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\", \"code\": \"toolong\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "code")))
        .andExpect(jsonPath("$.errors[0].message", notNullValue()));
  }

  @Test
  void malformedJsonIsAValidationErrorWithAnEmptyErrorsList() throws Exception {
    mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors").isEmpty());
  }

  @Test
  void constrainedRequestParamListsTheParameter() throws Exception {
    // Spring's built-in method validation (HandlerMethodValidationException), e.g. page size <=
    // 100.
    mvc.perform(get("/probe/page").param("size", "500"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("size"))
        .andExpect(jsonPath("$.errors[0].message", notNullValue()));
  }

  @Test
  void wrongTypeInBodyNamesTheField() throws Exception {
    mvc.perform(
            post("/probe/typed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startAt\": \"tomorrow\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("startAt"));
  }

  @Test
  void constraintViolationInAServiceIsAValidationError() throws Exception {
    mvc.perform(
            post("/probe/service-validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\", \"code\": \"ok\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  void notFoundExceptionBecomes404() throws Exception {
    mvc.perform(get("/probe/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.type").value("https://appointment-prep/errors/not-found"))
        .andExpect(jsonPath("$.detail").value("Booking 42 was not found."))
        .andExpect(jsonPath("$.instance").value("/probe/not-found"));
  }

  @Test
  @WithMockUser(roles = "CUSTOMER")
  void accessDeniedFromMethodSecurityIsForbidden() throws Exception {
    mvc.perform(get("/probe/denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void accessDeniedForAnAnonymousCallerIsUnauthenticated() throws Exception {
    // Like ExceptionTranslationFilter: not logged in means 401, so the SPA sends them to login.
    mvc.perform(get("/probe/denied"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void authenticationFailureInsideAControllerIsUnauthenticated() throws Exception {
    mvc.perform(get("/probe/no-auth"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void unexpectedErrorHidesInternals() throws Exception {
    MvcResult result =
        mvc.perform(get("/probe/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain("secret internal detail")
        .doesNotContain("IllegalStateException")
        .doesNotContain("at com.appointmentprep");
  }

  @Test
  void unknownRouteIsANotFoundProblemWithRequestId() throws Exception {
    mvc.perform(get("/no-such-route").header("X-Request-Id", "r-404"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.requestId").value("r-404"));
  }

  @Test
  void unsupportedMethodIsAProblemWithRequestId() throws Exception {
    mvc.perform(get("/probe/validate").header("X-Request-Id", "r-405"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.requestId").value("r-405"));
  }

  @Test
  void requestIdIsEchoedInHeaderAndBody() throws Exception {
    mvc.perform(get("/probe/not-found").header("X-Request-Id", "abc"))
        .andExpect(header().string("X-Request-Id", "abc"))
        .andExpect(jsonPath("$.requestId").value("abc"));
  }

  @Test
  void requestIdIsGeneratedWhenMissing() throws Exception {
    MvcResult result =
        mvc.perform(get("/probe/not-found"))
            .andExpect(header().string("X-Request-Id", not(emptyOrNullString())))
            .andReturn();

    String generated = result.getResponse().getHeader("X-Request-Id");
    mvc.perform(get("/probe/not-found")).andExpect(header().string("X-Request-Id", not(generated)));
    assertThat(result.getResponse().getContentAsString())
        .contains("\"requestId\":\"" + generated + "\"");
  }

  @Test
  void unsafeRequestIdIsReplaced() throws Exception {
    // Header values end up in logs; reject anything that could forge log lines.
    mvc.perform(get("/probe/not-found").header("X-Request-Id", "abc\nFAKE LOG LINE"))
        .andExpect(header().string("X-Request-Id", not(containsString("FAKE"))));
  }
}
