package com.sportsapp.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  ProblemDetail handle(ApiException e) {
    return problem(e.status(), e.code());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handle(MethodArgumentNotValidException e) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError f : e.getBindingResult().getFieldErrors()) {
      errors.putIfAbsent(f.getField(), f.getCode());
    }
    ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "VALIDATION");
    p.setProperty("errors", errors);
    return p;
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ProblemDetail handle(ObjectOptimisticLockingFailureException e) {
    return problem(HttpStatus.CONFLICT, "STALE");
  }

  private static ProblemDetail problem(HttpStatus status, String code) {
    ProblemDetail p = ProblemDetail.forStatus(status);
    p.setProperty("code", code);
    return p;
  }
}
