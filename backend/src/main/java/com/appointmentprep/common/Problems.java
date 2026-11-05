package com.appointmentprep.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds and writes RFC 7807 bodies outside Spring MVC (e.g. security filters), with the same shape
 * as {@link GlobalExceptionHandler}: {@code type}, {@code title}, {@code code}, {@code instance}
 * and {@code requestId}.
 */
@Component
public class Problems {

  private final ObjectMapper json;

  public Problems(ObjectMapper json) {
    this.json = json;
  }

  /** Sets type, title and code for an error code on an existing problem. */
  public static void applyCode(ProblemDetail problem, ErrorCode code) {
    problem.setType(code.type());
    problem.setTitle(code.title());
    problem.setProperty("code", code.name());
  }

  public void write(
      HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
    applyCode(problem, code);
    problem.setInstance(URI.create(request.getRequestURI()));
    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    if (requestId != null) {
      problem.setProperty("requestId", requestId);
    }
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), problem);
  }
}
