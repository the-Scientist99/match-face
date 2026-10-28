package com.sportsapp.player;

import com.sportsapp.common.Auth;
import com.sportsapp.common.RateLimiter;
import com.sportsapp.player.PlayerDtos.ClaimInviteResponse;
import com.sportsapp.player.PlayerDtos.ClaimPreviewResponse;
import com.sportsapp.player.PlayerDtos.ClaimResultResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
public class ClaimController {

  private final ClaimService claims;
  private final RateLimiter rateLimiter;

  public ClaimController(ClaimService claims, RateLimiter rateLimiter) {
    this.claims = claims;
    this.rateLimiter = rateLimiter;
  }

  @PostMapping("/{id}/claims")
  @ResponseStatus(HttpStatus.CREATED)
  ClaimInviteResponse invite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return claims.invite(Auth.userId(jwt), id);
  }

  /** Open to anonymous viewers, so the link can be previewed before logging in. */
  @GetMapping("/claims/{token}")
  ClaimPreviewResponse preview(@PathVariable String token) {
    return claims.preview(token);
  }

  @PostMapping("/claims/{token}")
  ClaimResultResponse claim(@AuthenticationPrincipal Jwt jwt, @PathVariable String token, HttpServletRequest http) {
    UUID me = Auth.userId(jwt);
    rateLimiter.check("claim", http.getRemoteAddr());
    return new ClaimResultResponse(claims.claim(me, token));
  }
}
