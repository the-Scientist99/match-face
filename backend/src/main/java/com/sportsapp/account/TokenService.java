package com.sportsapp.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

  static final String ISSUER = "sportsapp";
  private static final Duration ACCESS_TTL = Duration.ofMinutes(15);

  private final JwtEncoder encoder;
  private final Clock clock;

  public TokenService(JwtEncoder encoder, Clock clock) {
    this.encoder = encoder;
    this.clock = clock;
  }

  public String accessToken(UUID userId) {
    Instant now = clock.instant();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(ISSUER).subject(userId.toString()).issuedAt(now).expiresAt(now.plus(ACCESS_TTL)).build();
    return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
  }
}
