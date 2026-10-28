package com.sportsapp.match;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.ApiException;
import com.sportsapp.match.MatchDtos.CreateMatchRequest;
import com.sportsapp.match.MatchDtos.LineupEntry;
import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.match.MatchDtos.PatchMatchRequest;
import com.sportsapp.player.PlayerService;
import com.sportsapp.sport.Sport;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Match writes. Each one checks access, then takes the match row lock, then validates. */
@Service
public class MatchService {

  private final MatchRepository matches;
  private final MatchLineupRepository lineups;
  private final AccessService access;
  private final PlayerService players;
  private final MatchViews views;
  private final JdbcClient jdbc;

  public MatchService(MatchRepository matches, MatchLineupRepository lineups, AccessService access, PlayerService players,
      MatchViews views, JdbcClient jdbc) {
    this.matches = matches;
    this.lineups = lineups;
    this.access = access;
    this.players = players;
    this.views = views;
    this.jdbc = jdbc;
  }

  /** A team match (two team ids, both admined by the creator) or a pickup (two labels). */
  @Transactional
  public UUID create(UUID userId, CreateMatchRequest req) {
    boolean teams = req.homeTeamId() != null && req.awayTeamId() != null && req.homeLabel() == null && req.awayLabel() == null;
    boolean pickup = req.homeTeamId() == null && req.awayTeamId() == null && req.homeLabel() != null && req.awayLabel() != null;
    if (!teams && !pickup) throw ApiException.badRequest("INVALID_SIDES");
    Match m = new Match(Sport.FOOTBALL, MatchStatus.SCHEDULED);
    if (teams) {
      if (req.homeTeamId().equals(req.awayTeamId())) throw ApiException.badRequest("SAME_TEAM");
      access.requireAdmin(userId, ResourceType.TEAM, req.homeTeamId());
      access.requireAdmin(userId, ResourceType.TEAM, req.awayTeamId());
      m.setHomeTeamId(req.homeTeamId());
      m.setAwayTeamId(req.awayTeamId());
    } else {
      m.setHomeLabel(req.homeLabel());
      m.setAwayLabel(req.awayLabel());
    }
    m.setKickoffAt(req.kickoffAt());
    m.setVenue(req.venue());
    m.setVisibility(req.visibility());
    UUID id = matches.save(m).getId();
    access.grantOwner(userId, ResourceType.MATCH, id);
    return id;
  }

  @Transactional
  public void patch(UUID userId, UUID matchId, PatchMatchRequest req) {
    Match m = lockAsAdmin(userId, matchId);
    requireVersion(m, req.version());
    if (req.kickoffAt() != null) m.setKickoffAt(req.kickoffAt());
    if (req.venue() != null) m.setVenue(MatchDtos.trimToNull(req.venue()));
    if (req.visibility() != null) {
      if (m.getStageId() != null) throw ApiException.badRequest("INVALID_FIELD"); // competition matches use its visibility
      m.setVisibility(req.visibility());
    }
  }

  /**
   * Replaces the lineup. Players must be the admin's players, or already in this lineup (they may have left
   * the roster that made them available since).
   */
  @Transactional
  public void setLineup(UUID userId, UUID matchId, List<LineupEntry> entries) {
    Match m = lockAsAdmin(userId, matchId);
    requireScheduled(m);
    Set<UUID> ids = entries.stream().map(LineupEntry::playerId).collect(Collectors.toSet());
    if (ids.size() != entries.size()) throw ApiException.badRequest("DUPLICATE_PLAYER");
    List<MatchLineup> current = lineups.findByMatchId(matchId);
    Set<UUID> allowed = new HashSet<>(players.myPlayerIds(userId));
    current.forEach(l -> allowed.add(l.getPlayerId()));
    if (!allowed.containsAll(ids)) throw ApiException.badRequest("PLAYER_NOT_AVAILABLE");
    lineups.deleteByMatchId(matchId);
    lineups.saveAll(entries.stream().map(e -> new MatchLineup(matchId, e.playerId(), e.side(), e.inGoal())).toList());
    matches.bumpVersion(matchId);
  }

  /** Matches I hold a grant for or play in: unscheduled first, then newest kickoff, then newest created. */
  @Transactional(readOnly = true)
  public List<MatchSummary> mine(UUID userId) {
    List<UUID> ids = jdbc.sql("""
            SELECT BIN_TO_UUID(m.id) FROM sport_match m
            WHERE EXISTS (SELECT 1 FROM admin_grant g
                          WHERE g.resource_type = 'MATCH' AND g.resource_id = m.id AND g.user_id = UUID_TO_BIN(:u))
               OR EXISTS (SELECT 1 FROM match_lineup l JOIN player p ON p.id = l.player_id
                          WHERE l.match_id = m.id AND p.user_id = UUID_TO_BIN(:u))
            ORDER BY m.kickoff_at IS NULL DESC, m.kickoff_at DESC, m.created_at DESC
            """)
        .param("u", userId.toString())
        .query(UUID.class).list();
    return views.summaries(ids);
  }

  private Match lockAsAdmin(UUID userId, UUID matchId) {
    access.requireAdmin(userId, ResourceType.MATCH, matchId);
    return matches.lockForUpdate(matchId).orElseThrow(ApiException::notFound);
  }

  private static void requireVersion(Match m, long version) {
    if (m.getVersion() != version) throw ApiException.conflict("STALE");
  }

  /** Lineup and events can change only while the match is SCHEDULED; a finished one must be reopened first. */
  private static void requireScheduled(Match m) {
    if (m.getStatus() != MatchStatus.SCHEDULED) throw ApiException.conflict("MATCH_FINISHED");
  }
}
