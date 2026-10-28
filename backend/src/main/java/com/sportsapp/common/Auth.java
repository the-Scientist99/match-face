package com.sportsapp.common;

import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the caller's user id from the {@code @AuthenticationPrincipal Jwt} a controller receives. */
public final class Auth {

  private Auth() {}

  public static UUID userId(@Nullable Jwt jwt) {
    if (jwt == null) throw ApiException.unauthorized();
    return UUID.fromString(jwt.getSubject());
  }

  @Nullable
  public static UUID userIdOrNull(@Nullable Jwt jwt) {
    return jwt == null ? null : UUID.fromString(jwt.getSubject());
  }
}
