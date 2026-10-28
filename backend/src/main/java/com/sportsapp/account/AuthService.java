package com.sportsapp.account;

import com.sportsapp.account.AccountDtos.AuthResponse;
import com.sportsapp.account.AccountDtos.LoginRequest;
import com.sportsapp.account.AccountDtos.MeResponse;
import com.sportsapp.account.AccountDtos.PatchMeRequest;
import com.sportsapp.account.AccountDtos.RegisterRequest;
import com.sportsapp.common.ApiException;
import com.sportsapp.common.Tokens;
import com.sportsapp.player.Player;
import com.sportsapp.player.PlayerService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  static final Duration REFRESH_TTL = Duration.ofDays(30);
  /** BCrypt only reads the first 72 bytes of a password, and Spring's encoder refuses longer ones. */
  private static final int BCRYPT_MAX_BYTES = 72;

  /** A login result: the body for the client plus the raw refresh token for the cookie. */
  public record Session(AuthResponse body, String refreshToken) {}

  private final UserAccountRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PlayerService players;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokens;
  private final Clock clock;
  /** Compared against when the email is unknown, so both failures take the same BCrypt time. */
  private final String dummyHash;

  public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens, PlayerService players,
      PasswordEncoder passwordEncoder, TokenService tokens, Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.players = players;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
    this.clock = clock;
    this.dummyHash = passwordEncoder.encode("not-a-real-password");
  }

  @Transactional
  public Session register(RegisterRequest req) {
    if (req.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
      throw ApiException.badRequest("PASSWORD_TOO_LONG");
    }
    if (users.findByEmail(req.email()).isPresent()) throw ApiException.conflict("EMAIL_TAKEN");
    UserAccount user;
    try {
      user = users.saveAndFlush(new UserAccount(req.email(), passwordEncoder.encode(req.password()), req.locale()));
    } catch (DataIntegrityViolationException e) { // a concurrent registration won the unique index
      throw ApiException.conflict("EMAIL_TAKEN");
    }
    Player player = players.createOwn(user.getId(), req.displayName());
    return session(user, player);
  }

  @Transactional
  public Session login(LoginRequest req) {
    UserAccount user = users.findByEmail(req.email()).orElse(null);
    boolean ok = passwordEncoder.matches(req.password(), user == null ? dummyHash : user.getPasswordHash());
    if (user == null || !ok) throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS");
    return session(user, players.ownPlayer(user.getId()));
  }

  /** Rotates the token. Reusing an already-rotated token revokes every session of its user, then fails. */
  @Transactional(noRollbackFor = ApiException.class)
  public Session refresh(String raw) {
    if (raw == null || raw.isEmpty()) throw ApiException.unauthorized();
    Instant now = clock.instant();
    RefreshToken t = refreshTokens.findByTokenHash(Tokens.sha256Hex(raw)).orElseThrow(ApiException::unauthorized);
    if (t.getRevokedAt() != null) {
      refreshTokens.revokeAllForUser(t.getUserId(), now);
      throw ApiException.unauthorized();
    }
    if (!now.isBefore(t.getExpiresAt())) throw ApiException.unauthorized();
    t.setRevokedAt(now);
    UserAccount user = users.findById(t.getUserId()).orElseThrow(ApiException::unauthorized);
    return session(user, players.ownPlayer(user.getId()));
  }

  @Transactional
  public void logout(String raw) {
    if (raw == null || raw.isEmpty()) return;
    refreshTokens.findByTokenHash(Tokens.sha256Hex(raw))
        .filter(t -> t.getRevokedAt() == null)
        .ifPresent(t -> t.setRevokedAt(clock.instant()));
  }

  @Transactional(readOnly = true)
  public MeResponse me(UUID userId) {
    UserAccount user = users.findById(userId).orElseThrow(ApiException::unauthorized);
    return me(user, players.ownPlayer(userId));
  }

  @Transactional
  public MeResponse patchMe(UUID userId, PatchMeRequest req) {
    UserAccount user = users.findById(userId).orElseThrow(ApiException::unauthorized);
    if (req.locale() != null) user.setLocale(req.locale());
    Player player = req.displayName() != null ? players.renameOwn(userId, req.displayName()) : players.ownPlayer(userId);
    return me(user, player);
  }

  private Session session(UserAccount user, Player player) {
    String raw = Tokens.newToken();
    refreshTokens.save(new RefreshToken(user.getId(), Tokens.sha256Hex(raw), clock.instant().plus(REFRESH_TTL), null));
    return new Session(new AuthResponse(tokens.accessToken(user.getId()), me(user, player)), raw);
  }

  private static MeResponse me(UserAccount user, Player player) {
    return new MeResponse(user.getId(), user.getEmail(), user.getLocale(), player.getId(), player.getName());
  }
}
