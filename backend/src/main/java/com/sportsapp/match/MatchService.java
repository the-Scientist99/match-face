package com.sportsapp.match;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.ApiException;
import com.sportsapp.match.MatchDtos.CreateMatchRequest;
import com.sportsapp.match.MatchDtos.EventRequest;
import com.sportsapp.match.MatchDtos.FinishRequest;
import com.sportsapp.match.MatchDtos.LineupEntry;
import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.match.MatchDtos.PatchMatchRequest;
import com.sportsapp.player.PlayerService;
import com.sportsapp.sport.Sport;
import com.sportsapp.sport.SportRules;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
  private final MatchEventRepository events;
  private final AccessService access;
  private final PlayerService players;
  private final MatchViews views;
  private final JdbcClient jdbc;

  public MatchService(MatchRepository matches, MatchLineupRepository lineups, MatchEventRepository events, AccessService access,
      PlayerService players, MatchViews views, JdbcClient jdbc) {
    this.matches = matches;
    this.lineups = lineups;
    this.events = events;
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
    // a player an event names (as player or assister) must stay in the lineup, on that event's side
    Map<UUID, Side> newSides = entries.stream().collect(Collectors.toMap(LineupEntry::playerId, LineupEntry::side));
    for (MatchEvent e : events.findByMatchId(matchId)) {
      for (UUID p : new UUID[] {e.getPlayerId(), e.getAssistPlayerId()}) {
        if (p != null && newSides.get(p) != e.getSide()) throw ApiException.conflict("PLAYER_HAS_EVENTS");
      }
    }
    lineups.deleteByMatchId(matchId);
    lineups.saveAll(entries.stream().map(e -> new MatchLineup(matchId, e.playerId(), e.side(), e.inGoal())).toList());
    matches.bumpVersion(matchId);
  }

  @Transactional
  public void addEvent(UUID userId, UUID matchId, EventRequest req) {
    Match m = lockAsAdmin(userId, matchId);
    validateEvent(m, req, null);
    events.save(new MatchEvent(matchId, req.type(), req.side(), req.playerId(), req.assistPlayerId(), req.minute()));
    matches.bumpVersion(matchId);
  }

  @Transactional
  public void updateEvent(UUID userId, UUID matchId, UUID eventId, EventRequest req) {
    Match m = lockAsAdmin(userId, matchId);
    MatchEvent e = eventOf(matchId, eventId);
    validateEvent(m, req, eventId);
    e.setType(req.type());
    e.setSide(req.side());
    e.setPlayerId(req.playerId());
    e.setAssistPlayerId(req.assistPlayerId());
    e.setMinute(req.minute());
    matches.bumpVersion(matchId);
  }

  @Transactional
  public void deleteEvent(UUID userId, UUID matchId, UUID eventId) {
    Match m = lockAsAdmin(userId, matchId);
    requireScheduled(m);
    events.delete(eventOf(matchId, eventId));
    matches.bumpVersion(matchId);
  }

  /** SCHEDULED → FINISHED. M6 adds the cup rules (penalty winner, advancement) here. */
  @Transactional
  public void finish(UUID userId, UUID matchId, FinishRequest r) {
    Match m = lockAsAdmin(userId, matchId);
    requireVersion(m, r.version());
    requireScheduled(m);
    m.setStatus(MatchStatus.FINISHED);
  }

  /** FINISHED or CANCELLED → SCHEDULED; a scheduled match stays as it is. M6 adds the cup rules here. */
  @Transactional
  public void reopen(UUID userId, UUID matchId, long version) {
    Match m = lockAsAdmin(userId, matchId);
    requireVersion(m, version);
    if (m.getStatus() != MatchStatus.SCHEDULED) m.setStatus(MatchStatus.SCHEDULED);
  }

  /** SCHEDULED → CANCELLED. M6 forbids it for cup matches. */
  @Transactional
  public void cancel(UUID userId, UUID matchId, long version) {
    Match m = lockAsAdmin(userId, matchId);
    requireVersion(m, version);
    requireScheduled(m);
    m.setStatus(MatchStatus.CANCELLED);
  }

  /**
   * In order, first failure wins: match not SCHEDULED; type not allowed by the sport; a card or MOTM
   * without a player, or a bad assist; a player or assister not in the lineup on {@code side}; a second MOTM.
   * {@code editing}: the event being replaced, which doesn't count as an existing MOTM.
   */
  private void validateEvent(Match m, EventRequest req, UUID editing) {
    requireScheduled(m);
    if (!SportRules.of(m.getSport()).allowedEventTypes().contains(req.type())) throw ApiException.badRequest("INVALID_EVENT");
    boolean needsPlayer = req.type() == EventType.YELLOW || req.type() == EventType.RED || req.type() == EventType.MOTM;
    if (needsPlayer && req.playerId() == null) throw ApiException.badRequest("INVALID_EVENT");
    if (req.assistPlayerId() != null
        && (req.type() != EventType.GOAL || req.playerId() == null || req.assistPlayerId().equals(req.playerId()))) {
      throw ApiException.badRequest("INVALID_EVENT");
    }
    Map<UUID, Side> sides = lineups.findByMatchId(m.getId()).stream()
        .collect(Collectors.toMap(MatchLineup::getPlayerId, MatchLineup::getSide));
    for (UUID p : new UUID[] {req.playerId(), req.assistPlayerId()}) {
      if (p != null && sides.get(p) != req.side()) throw ApiException.badRequest("PLAYER_NOT_IN_LINEUP");
    }
    if (req.type() == EventType.MOTM && events.findByMatchId(m.getId()).stream()
        .anyMatch(e -> e.getType() == EventType.MOTM && !e.getId().equals(editing))) {
      throw ApiException.badRequest("INVALID_EVENT");
    }
  }

  private MatchEvent eventOf(UUID matchId, UUID eventId) {
    return events.findById(eventId).filter(e -> e.getMatchId().equals(matchId)).orElseThrow(ApiException::notFound);
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
