package com.sportsapp.account;

import com.sportsapp.account.AccountDtos.MeResponse;
import com.sportsapp.account.AccountDtos.PatchMeRequest;
import com.sportsapp.common.Auth;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

  private final AuthService auth;

  public MeController(AuthService auth) {
    this.auth = auth;
  }

  @GetMapping
  MeResponse me(@AuthenticationPrincipal Jwt jwt) {
    return auth.me(Auth.userId(jwt));
  }

  @PatchMapping
  MeResponse patch(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PatchMeRequest req) {
    return auth.patchMe(Auth.userId(jwt), req);
  }
}
