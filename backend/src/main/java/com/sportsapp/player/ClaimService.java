package com.sportsapp.player;

import com.sportsapp.common.ApiException;
import com.sportsapp.common.Tokens;
import com.sportsapp.player.PlayerDtos.ClaimInviteResponse;
import com.sportsapp.player.PlayerDtos.ClaimPreviewResponse;
import com.sportsapp.stats.PlayerStatsQuery;
import com.sportsapp.stats.StatsDtos.PlayerTotals;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guest invites and claims. Every user already has a player, so claiming a guest always merges it into the
 * claimer's player: its lineups, events and roster places move over, and the guest is deleted.
 */
@Service
public class ClaimService {

  static final Duration TTL = Duration.ofDays(14);

  private final PlayerClaimRepository claims;
  private final PlayerService players;
  private final PlayerStatsQuery stats;
  private final JdbcClient jdbc;
  private final Clock clock;

  public ClaimService(PlayerClaimRepository claims, PlayerService players, PlayerStatsQuery stats, JdbcClient jdbc, Clock clock) {
    this.claims = claims;
    this.players = players;
    this.stats = stats;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Only the guest's creator, only while unclaimed. Replaces any earlier invite for this guest. */
  @Transactional
  public ClaimInviteResponse invite(UUID userId, UUID playerId) {
    Player guest = players.getRequired(playerId);
    if (!userId.equals(guest.getCreatedByUserId())) throw ApiException.forbidden();
    if (guest.getUserId() != null) throw invalid();
    claims.deleteByPlayerId(playerId);
    String token = Tokens.newToken();
    Instant expiresAt = clock.instant().plus(TTL);
    claims.save(new PlayerClaim(playerId, Tokens.sha256Hex(token), expiresAt));
    return new ClaimInviteResponse(token, expiresAt);
  }

  /** Counts every finished match: the token holder is the guest's intended owner. */
  @Transactional(readOnly = true)
  public ClaimPreviewResponse preview(String token) {
    Player guest = guestOf(claims.findByTokenHash(Tokens.sha256Hex(token)).orElseThrow(ClaimService::invalid));
    PlayerTotals totals = stats.totalsOfAllMatches(guest.getId());
    return new ClaimPreviewResponse(guest.getId(), guest.getName(), totals.appearances(), totals.goals());
  }

  /** Merges the guest into the user's own player. Returns that player's id. */
  @Transactional
  public UUID claim(UUID userId, String token) {
    // The claim row lock comes before any other read: at REPEATABLE READ the first plain read fixes the
    // snapshot, and a claim that waited on the lock must see what the other one committed.
    PlayerClaim claim = claims.lockByTokenHash(Tokens.sha256Hex(token)).orElseThrow(ClaimService::invalid);
    Player guest = guestOf(claim);
    UUID mine = players.ownPlayer(userId).getId();
    String g = guest.getId().toString();
    String m = mine.toString();
    boolean sameMatch = jdbc.sql("""
            SELECT EXISTS (SELECT 1 FROM match_lineup a JOIN match_lineup b ON b.match_id = a.match_id
                           WHERE a.player_id = UUID_TO_BIN(:g) AND b.player_id = UUID_TO_BIN(:m))
            """)
        .param("g", g).param("m", m).query(Boolean.class).single();
    if (sameMatch) throw ApiException.conflict("MERGE_CONFLICT");
    for (String sql : new String[] {
        "UPDATE match_lineup SET player_id = UUID_TO_BIN(:m) WHERE player_id = UUID_TO_BIN(:g)",
        "UPDATE match_event SET player_id = UUID_TO_BIN(:m) WHERE player_id = UUID_TO_BIN(:g)",
        "UPDATE match_event SET assist_player_id = UUID_TO_BIN(:m) WHERE assist_player_id = UUID_TO_BIN(:g)",
        // a team both are on keeps one row: drop the guest's
        "DELETE tp FROM team_player tp JOIN team_player t2 ON t2.team_id = tp.team_id AND t2.player_id = UUID_TO_BIN(:m) "
            + "WHERE tp.player_id = UUID_TO_BIN(:g)",
        "UPDATE team_player SET player_id = UUID_TO_BIN(:m) WHERE player_id = UUID_TO_BIN(:g)",
        "DELETE FROM player WHERE id = UUID_TO_BIN(:g)", // cascades to its claims, so the token is spent
    }) {
      var spec = jdbc.sql(sql);
      if (sql.contains(":m")) spec = spec.param("m", m);
      spec.param("g", g).update();
    }
    return mine;
  }

  /** The unclaimed guest a live claim points at; anything else is CLAIM_INVALID. */
  private Player guestOf(PlayerClaim claim) {
    if (!clock.instant().isBefore(claim.getExpiresAt())) throw invalid();
    Player guest = players.getRequired(claim.getPlayerId());
    if (guest.getUserId() != null) throw invalid();
    return guest;
  }

  private static ApiException invalid() {
    return ApiException.conflict("CLAIM_INVALID");
  }
}
