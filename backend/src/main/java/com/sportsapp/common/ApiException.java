package com.sportsapp.common;

import org.springframework.http.HttpStatus;

/** An error the API returns as a ProblemDetail with a machine-readable {@code code}. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code) {
    super(code);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() { return status; }

  public String code() { return code; }

  public static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND"); }

  public static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN"); }

  public static ApiException unauthorized() { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"); }

  public static ApiException badRequest(String code) { return new ApiException(HttpStatus.BAD_REQUEST, code); }

  public static ApiException conflict(String code) { return new ApiException(HttpStatus.CONFLICT, code); }

  public static ApiException tooManyRequests() { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"); }
}
