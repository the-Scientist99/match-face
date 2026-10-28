package com.sportsapp.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API as a ProblemDetail with a {@code code} property, including the ones
 * Spring MVC raises itself (bad JSON, wrong method, missing parameter, …), whose code is the HTTP
 * status name, e.g. {@code BAD_REQUEST}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(ApiException.class)
  ProblemDetail handle(ApiException e) {
    return problem(e.status(), e.code());
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ProblemDetail handle(ObjectOptimisticLockingFailureException e) {
    return problem(HttpStatus.CONFLICT, "STALE");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), constraintName(f)));
    return validation(errors);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().stream().findFirst()
        .ifPresent(err -> errors.putIfAbsent(r.getMethodParameter().getParameterName(), constraintName(err))));
    return validation(errors);
  }

  /** A path id that isn't even well-formed can't name an existing resource, so it's a 404, not a 400. */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (ex instanceof MethodArgumentTypeMismatchException m && m.getParameter().hasParameterAnnotation(PathVariable.class)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }
    return super.handleTypeMismatch(ex, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    ProblemDetail problem = body instanceof ProblemDetail p ? p : ProblemDetail.forStatus(statusCode);
    if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
      HttpStatus resolved = HttpStatus.resolve(statusCode.value());
      problem.setProperty("code", resolved != null ? resolved.name() : "UNKNOWN");
    }
    return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
  }

  private static ResponseEntity<Object> validation(Map<String, String> errors) {
    ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "VALIDATION");
    p.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(p);
  }

  /** The constraint's simple name ("NotBlank", "Min"): the most general of the resolvable codes. */
  private static String constraintName(MessageSourceResolvable error) {
    String[] codes = error.getCodes();
    return codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
  }

  private static ProblemDetail problem(HttpStatus status, String code) {
    ProblemDetail p = ProblemDetail.forStatus(status);
    p.setProperty("code", code);
    return p;
  }
}
