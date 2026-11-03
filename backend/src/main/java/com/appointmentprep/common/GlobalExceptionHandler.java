package com.appointmentprep.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into RFC 7807 problem+json with a {@code code} from TECHNICAL_DESIGN §9.3 and
 * the {@code requestId}. Spring MVC's own exceptions (404 route, 405, 415, …) are handled by the
 * base class and enriched in {@link #handleExceptionInternal}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** One entry of {@code errors[]} for {@code VALIDATION_ERROR}. */
  public record FieldProblem(String field, String message) {}

  @ExceptionHandler(DomainException.class)
  ResponseEntity<Object> handleDomain(DomainException ex, WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(ex.code().status(), ex.getMessage());
    withCode(body, ex.code());
    return handleExceptionInternal(ex, body, new HttpHeaders(), ex.code().status(), request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
    log.error("Unhandled exception", ex);
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    body.setTitle("Internal server error");
    return handleExceptionInternal(
        ex, body, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldProblem> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldProblem(e.getField(), e.getDefaultMessage()))
            .toList();
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Some fields are invalid.");
    withCode(body, ErrorCode.VALIDATION_ERROR);
    body.setProperty("errors", errors);
    return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request body is malformed.");
    withCode(body, ErrorCode.VALIDATION_ERROR);
    return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
  }

  /** Every error response passes through here: add the code, instance and request ID. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    // Spring's own handlers pass a null body; super builds the ProblemDetail, so enrich afterwards.
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, status, request);
    if (response != null && response.getBody() instanceof ProblemDetail problem) {
      if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
        defaultCodeFor(status).ifPresent(code -> withCode(problem, code));
      }
      if (problem.getInstance() == null) {
        HttpServletRequest servletRequest =
            ((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class);
        if (servletRequest != null) {
          problem.setInstance(URI.create(servletRequest.getRequestURI()));
        }
      }
      problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
    }
    return response;
  }

  private static void withCode(ProblemDetail problem, ErrorCode code) {
    problem.setType(code.type());
    problem.setTitle(code.title());
    problem.setProperty("code", code.name());
  }

  /** Codes for framework errors that have an obvious §9.3 equivalent. */
  private static Optional<ErrorCode> defaultCodeFor(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> Optional.of(ErrorCode.VALIDATION_ERROR);
      case 404 -> Optional.of(ErrorCode.NOT_FOUND);
      default -> Optional.empty();
    };
  }
}
