package com.sportsapp.account;

import com.sportsapp.account.AccountDtos.AuthResponse;
import com.sportsapp.account.AccountDtos.LoginRequest;
import com.sportsapp.account.AccountDtos.RegisterRequest;
import com.sportsapp.common.ApiException;
import com.sportsapp.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final String COOKIE = "refresh_token";

  private final AuthService auth;
  private final RateLimiter rateLimiter;
  private final boolean cookieSecure;

  public AuthController(AuthService auth, RateLimiter rateLimiter, @Value("${app.auth.cookie-secure}") boolean cookieSecure) {
    this.auth = auth;
    this.rateLimiter = rateLimiter;
    this.cookieSecure = cookieSecure;
  }

  @PostMapping("/register")
  ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
    rateLimiter.check("register", http.getRemoteAddr());
    return withCookie(HttpStatus.CREATED, auth.register(req));
  }

  @PostMapping("/login")
  ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
    rateLimiter.check("login", http.getRemoteAddr());
    return withCookie(HttpStatus.OK, auth.login(req));
  }

  @PostMapping("/refresh")
  ResponseEntity<?> refresh(@CookieValue(name = COOKIE, required = false) String raw) {
    try {
      return withCookie(HttpStatus.OK, auth.refresh(raw));
    } catch (ApiException e) { // a dead token is useless to the browser: clear it along with the 401
      ProblemDetail problem = ProblemDetail.forStatus(e.status());
      problem.setProperty("code", e.code());
      return ResponseEntity.status(e.status()).header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).body(problem);
    }
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String raw) {
    auth.logout(raw);
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).build();
  }

  private ResponseEntity<AuthResponse> withCookie(HttpStatus status, AuthService.Session s) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, cookie(s.refreshToken(), AuthService.REFRESH_TTL.toSeconds()).toString())
        .body(s.body());
  }

  private ResponseCookie cookie(String value, long maxAgeSeconds) {
    return ResponseCookie.from(COOKIE, value)
        .httpOnly(true).secure(cookieSecure).sameSite("Strict").path("/api/auth").maxAge(maxAgeSeconds).build();
  }
}
