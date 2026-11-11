package com.appointmentprep.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into RFC 7807 problem+json with a {@code code} from TECHNICAL_DESIGN §9.3 and
 * the {@code requestId}. Spring MVC's own exceptions (404 route, 405, 415, …) are handled by the
 * base class and enriched in {@link #handleExceptionInternal}. Every {@code VALIDATION_ERROR}
 * carries {@code errors[{field, message}]}, possibly empty.
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
    if (ex instanceof FieldValidationException invalid) {
      body.setProperty("errors", invalid.errors());
    }
    HttpHeaders headers = new HttpHeaders();
    if (ex instanceof RateLimitedException limited) {
      headers.set(HttpHeaders.RETRY_AFTER, Long.toString(limited.retryAfterSeconds()));
    }
    return handleExceptionInternal(ex, body, headers, ex.code().status(), request);
  }

  /** Bean Validation outside the controller boundary, e.g. in a {@code @Validated} service. */
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<Object> handleConstraintViolation(
      ConstraintViolationException ex, WebRequest request) {
    List<FieldProblem> errors =
        ex.getConstraintViolations().stream()
            .map(v -> new FieldProblem(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
    return validationError(ex, errors, "Some fields are invalid.", new HttpHeaders(), request);
  }

  /** Method security (@PreAuthorize) throws inside the controller, before the security filter. */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
    // Like ExceptionTranslationFilter: a caller who isn't logged in gets 401, not 403.
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
      return handleDomain(
          new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication is required."), request);
    }
    return handleDomain(
        new DomainException(ErrorCode.FORBIDDEN, "You don't have access to this."), request);
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
    return handleDomain(
        new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication is required."), request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
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
            .map(GlobalExceptionHandler::toProblem)
            .toList();
    return validationError(ex, errors, "Some fields are invalid.", headers, request);
  }

  /** Spring's built-in method validation: constraints directly on handler parameters. */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldProblem> errors = new ArrayList<>();
    for (ParameterValidationResult result : ex.getAllValidationResults()) {
      if (result instanceof ParameterErrors beanErrors) {
        beanErrors.getFieldErrors().forEach(e -> errors.add(toProblem(e)));
      } else {
        String parameter = result.getMethodParameter().getParameterName();
        result
            .getResolvableErrors()
            .forEach(e -> errors.add(new FieldProblem(parameter, e.getDefaultMessage())));
      }
    }
    return validationError(ex, errors, "Some fields are invalid.", headers, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    // A well-formed body with a value of the wrong type names the field; broken JSON doesn't.
    if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
      String field =
          mapping.getPath().stream()
              .map(
                  ref ->
                      ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
              .collect(Collectors.joining("."))
              .replace(".[", "[");
      return validationError(
          ex,
          List.of(new FieldProblem(field, "has an invalid value")),
          "The request body has a value of the wrong type.",
          headers,
          request);
    }
    return validationError(ex, List.of(), "The request body is malformed.", headers, request);
  }

  /** Every error response passes through here: add the code, instance and request ID. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (status.is5xxServerError()) {
      log.error("Request failed with {}", status.value(), ex);
    }
    // Spring's own handlers pass a null body; super builds the ProblemDetail, so enrich afterwards.
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, status, request);
    if (response != null && response.getBody() instanceof ProblemDetail problem) {
      if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
        defaultCodeFor(status).ifPresent(code -> withCode(problem, code));
      }
      if (ErrorCode.VALIDATION_ERROR.name().equals(codeOf(problem))
          && !problem.getProperties().containsKey("errors")) {
        problem.setProperty("errors", List.of());
      }
      if (problem.getInstance() == null) {
        HttpServletRequest servletRequest =
            ((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class);
        if (servletRequest != null) {
          problem.setInstance(URI.create(servletRequest.getRequestURI()));
        }
      }
      String requestId = MDC.get(RequestIdFilter.MDC_KEY);
      if (requestId != null) {
        problem.setProperty("requestId", requestId);
      }
    }
    return response;
  }

  private ResponseEntity<Object> validationError(
      Exception ex,
      List<FieldProblem> errors,
      String detail,
      HttpHeaders headers,
      WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    withCode(body, ErrorCode.VALIDATION_ERROR);
    body.setProperty("errors", errors);
    return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
  }

  private static FieldProblem toProblem(FieldError error) {
    return new FieldProblem(error.getField(), error.getDefaultMessage());
  }

  private static void withCode(ProblemDetail problem, ErrorCode code) {
    Problems.applyCode(problem, code);
  }

  private static String codeOf(ProblemDetail problem) {
    return problem.getProperties() == null ? null : (String) problem.getProperties().get("code");
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
