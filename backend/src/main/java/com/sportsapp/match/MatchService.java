package com.sportsapp.match;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.ApiException;
import com.sportsapp.common.Paging;
import com.sportsapp.common.Visibility;
import com.sportsapp.match.MatchDtos.CreateMatchRequest;
import com.sportsapp.match.MatchDtos.EventRequest;
import com.sportsapp.match.MatchDtos.FinishRequest;
import com.sportsapp.match.MatchDtos.LineupEntry;
import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.match.MatchDtos.OpenMatches;
import com.sportsapp.match.MatchDtos.PatchMatchRequest;
import com.sportsapp.player.PlayerService;
import com.sportsapp.team.TeamService;
import com.sportsapp.sport.Sport;
import com.sportsapp.sport.Score;
import com.sportsapp.sport.ScoreEvent;
import com.sportsapp.sport.SportRules;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Match writes. Each one takes the match row lock, then checks access, then validates. */
@Service
public class MatchService {

  private final MatchRepository matches;
  private final MatchLineupRepository lineups;
  private final MatchEventRepository events;
  private final MatchRsvpRepository rsvps;
  private final AccessService access;
  private final PlayerService players;
  private final TeamService teams;
  private final MatchViews views;
  private final JdbcClient jdbc;
  private final Clock clock;

  public MatchService(MatchRepository matches, MatchLineupRepository lineups, MatchEventRepository events, MatchRsvpRepository rsvps,
      AccessService access,
      PlayerService players, TeamService teams, MatchViews views, JdbcClient jdbc, Clock clock) {
    this.matches = matches;
    this.lineups = lineups;
    this.events = events;
    this.rsvps = rsvps;
    this.access = access;
    this.players = players;
    this.teams = teams;
    this.views = views;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** A team match (two team ids, both admined by the creator) or a pickup (two labels). */
  @Transactional
  public UUID create(UUID userId, CreateMatchRequest req) {
    boolean teams = req.homeTeamId() != null && req.awayTeamId() != null && req.homeLabel() == null && req.awayLabel() == null;
    boolean pickup = req.homeTeamId() == null && req.awayTeamId() == null && req.homeLabel() != null && req.awayLabel() != null;
    if (!teams && !pickup) throw ApiException.badRequest("INVALID_SIDES");
    requireFuture(req.kickoffAt());
    if (teams && req.maxPerSide() != null) throw ApiException.badRequest("INVALID_FIELD"); // team lineups come from rosters
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
    m.setMaxPerSide(req.maxPerSide());
    UUID id = matches.save(m).getId();
    access.grantOwner(userId, ResourceType.MATCH, id);
    return id;
  }

  @Transactional
  public void patch(UUID userId, UUID matchId, PatchMatchRequest req) {
    Match m = lockAsAdmin(userId, matchId);
    requireVersion(m, req.version());
    if (req.kickoffAt() != null) {
      requireFuture(req.kickoffAt());
      m.setKickoffAt(req.kickoffAt());
    }
    if (req.venue() != null) m.setVenue(MatchDtos.trimToNull(req.venue()));
    if (req.visibility() != null) {
      if (m.getStageId() != null) throw ApiException.badRequest("INVALID_FIELD"); // competition matches use its visibility
      m.setVisibility(req.visibility());
    }
    if (req.maxPerSide() != null) {
      if (m.getStageId() != null || m.getHomeTeamId() != null) throw ApiException.badRequest("INVALID_FIELD");
      m.setMaxPerSide(req.maxPerSide() == 0 ? null : req.maxPerSide()); // lowering it keeps who's already in
    }
  }

  /**
   * Replaces the lineup. Players must be the admin's players, or already in this lineup (they may have left
   * the roster that made them available since). In a competition match a side may also use its team's
   * roster, and a player picked for a side whose team the admin also admins, who isn't on its roster, is
   * added to it. A competition match's team admins pick just their side: {@code entries} can't name another
   * side, and that side's lineup stays as it is.
   */
  @Transactional
  public void setLineup(UUID userId, UUID matchId, List<LineupEntry> entries) {
    Match m = lockAsReader(userId, matchId);
    Set<Side> mySides = views.lineupSides(userId, matchId, m.getStageId() != null, m.getHomeTeamId(), m.getAwayTeamId());
    if (mySides.isEmpty() || !entries.stream().allMatch(e -> mySides.contains(e.side()))) throw ApiException.forbidden();
    requireScheduled(m);
    requireTeamsSet(m);
    List<MatchLineup> current = lineups.findByMatchId(matchId);
    List<LineupEntry> merged = new ArrayList<>(entries);
    current.stream().filter(l -> !mySides.contains(l.getSide()))
        .forEach(l -> merged.add(new LineupEntry(l.getPlayerId(), l.getSide(), l.isInGoal())));
    Set<UUID> ids = merged.stream().map(LineupEntry::playerId).collect(Collectors.toSet());
    if (ids.size() != merged.size()) throw ApiException.badRequest("DUPLICATE_PLAYER");
    Set<UUID> mine = players.myPlayerIds(userId);
    Set<UUID> inLineup = current.stream().map(MatchLineup::getPlayerId).collect(Collectors.toSet());
    Map<Side, UUID> sideTeams = new java.util.EnumMap<>(Side.class);
    if (m.getStageId() != null) {
      if (m.getHomeTeamId() != null) sideTeams.put(Side.HOME, m.getHomeTeamId());
      if (m.getAwayTeamId() != null) sideTeams.put(Side.AWAY, m.getAwayTeamId());
    }
    Map<Side, Set<UUID>> rosters = new java.util.EnumMap<>(Side.class);
    sideTeams.forEach((side, team) -> rosters.put(side, teams.rosterIds(team)));
    // someone else's team takes only its own roster, so an organizer's players can't land on it
    Set<Side> foreign = sideTeams.keySet().stream()
        .filter(side -> !access.canAdmin(userId, ResourceType.TEAM, sideTeams.get(side))).collect(Collectors.toSet());
    for (LineupEntry e : entries) {
      boolean onRoster = rosters.getOrDefault(e.side(), Set.of()).contains(e.playerId());
      boolean usable = inLineup.contains(e.playerId()) || (mine.contains(e.playerId()) && !foreign.contains(e.side()));
      if (!onRoster && !usable) throw ApiException.badRequest("PLAYER_NOT_AVAILABLE");
    }
    // a player an event names (as player or assister) must stay in the lineup, on that event's side
    Map<UUID, Side> newSides = merged.stream().collect(Collectors.toMap(LineupEntry::playerId, LineupEntry::side));
    for (MatchEvent e : events.findByMatchId(matchId)) {
      for (UUID p : new UUID[] {e.getPlayerId(), e.getAssistPlayerId()}) {
        if (p != null && newSides.get(p) != e.getSide()) throw ApiException.conflict("PLAYER_HAS_EVENTS");
      }
    }
    lineups.deleteByMatchId(matchId);
    lineups.saveAll(merged.stream().map(e -> new MatchLineup(matchId, e.playerId(), e.side(), e.inGoal())).toList());
    for (LineupEntry e : entries) {
      UUID team = sideTeams.get(e.side());
      if (team != null && !foreign.contains(e.side()) && !rosters.get(e.side()).contains(e.playerId())) teams.addToRoster(team, e.playerId());
    }
    matches.bumpVersion(matchId);
  }

  /** A SCHEDULED competition match; the competition's admins administer it, so it gets no grant of its own. */
  @Transactional
  public UUID createFixture(UUID stageId, int round, Sport sport, UUID homeTeamId, UUID awayTeamId) {
    return createFixture(stageId, round, null, sport, homeTeamId, awayTeamId);
  }

  /** A knockout match: {@code bracketPosition} set; sides may be null until earlier rounds decide them. */
  @Transactional
  public UUID createFixture(UUID stageId, int round, Integer bracketPosition, Sport sport, UUID homeTeamId, UUID awayTeamId) {
    Match m = new Match(sport, MatchStatus.SCHEDULED);
    m.setStageId(stageId);
    m.setRound(round);
    m.setBracketPosition(bracketPosition);
    m.setHomeTeamId(homeTeamId);
    m.setAwayTeamId(awayTeamId);
    return matches.save(m).getId();
  }

  /** The winner of {@code matchId} goes into {@code slot} of {@code nextMatchId}. */
  @Transactional
  public void linkNext(UUID matchId, UUID nextMatchId, Side slot) {
    Match m = matches.findById(matchId).orElseThrow(ApiException::notFound);
    m.setNextMatchId(nextMatchId);
    m.setNextSlot(slot);
  }

  /** The higher score, else the penalty winner (null if level without one). */
  public static Side winnerSide(Score score, Side penaltyWinner) {
    return score.home() > score.away() ? Side.HOME : score.away() > score.home() ? Side.AWAY : penaltyWinner;
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
    requireTeamsSet(m);
    events.delete(eventOf(matchId, eventId));
    matches.bumpVersion(matchId);
  }

  /**
   * SCHEDULED → FINISHED. A knockout match needs a winner: the higher score, or on a level score the
   * {@code penaltyWinner} (stored only then). The winner goes into the next match's slot right away.
   */
  @Transactional
  public void finish(UUID userId, UUID matchId, FinishRequest r) {
    Match m = lockAsAdmin(userId, matchId);
    requireVersion(m, r.version());
    requireScheduled(m);
    requireStarted(m);
    if (isKnockout(m)) {
      requireTeamsSet(m);
      Score score = SportRules.of(m.getSport()).score(events.findByMatchId(matchId).stream()
          .map(e -> new ScoreEvent(e.getType(), e.getSide())).toList());
      boolean level = score.home() == score.away();
      if (level && r.penaltyWinner() == null) throw ApiException.conflict("CUP_DRAW_NEEDS_PENALTIES");
      m.setPenaltyWinnerSide(level ? r.penaltyWinner() : null);
      Side winner = winnerSide(score, m.getPenaltyWinnerSide());
      if (m.getNextMatchId() != null) {
        Match next = lockNext(m);
        UUID team = winner == Side.HOME ? m.getHomeTeamId() : m.getAwayTeamId();
        if (m.getNextSlot() == Side.HOME) next.setHomeTeamId(team);
        else next.setAwayTeamId(team);
      }
    }
    m.setStatus(MatchStatus.FINISHED);
  }

  /**
   * FINISHED or CANCELLED → SCHEDULED; a scheduled match stays as it is. A finished knockout match can only
   * be reopened while its next match is SCHEDULED with no events; its winner then leaves that match, along
   * with that side's lineup there.
   */
  @Transactional
  public void reopen(UUID userId, UUID matchId, long version) {
    Match m = lockAsAdmin(userId, matchId);
    requireVersion(m, version);
    if (m.getStatus() == MatchStatus.SCHEDULED) return;
    if (isKnockout(m) && m.getNextMatchId() != null) {
      Match next = lockNext(m);
      boolean nextHasEvents = !jdbc.sql("SELECT BIN_TO_UUID(id) FROM match_event WHERE match_id = UUID_TO_BIN(:m) FOR UPDATE")
          .param("m", next.getId().toString()).query(String.class).list().isEmpty(); // locking read: sees events added while we waited
      if (next.getStatus() != MatchStatus.SCHEDULED || nextHasEvents) throw ApiException.conflict("LATER_ROUND_FINISHED");
      if (m.getNextSlot() == Side.HOME) next.setHomeTeamId(null);
      else next.setAwayTeamId(null);
      lineups.deleteByMatchIdAndSide(next.getId(), m.getNextSlot());
    }
    m.setPenaltyWinnerSide(null);
    m.setStatus(MatchStatus.SCHEDULED);
  }

  /** Moves a kickoff that is still ahead to now, for a game that starts early; otherwise nothing changes. */
  @Transactional
  public void start(UUID userId, UUID matchId, long version) {
    Match m = lockAsAdmin(userId, matchId);
    requireVersion(m, version);
    requireScheduled(m);
    if (m.getKickoffAt() != null && m.getKickoffAt().isAfter(clock.instant())) m.setKickoffAt(clock.instant());
  }

  /** SCHEDULED → CANCELLED. Not for knockout matches: the bracket needs a winner. */
  @Transactional
  public void cancel(UUID userId, UUID matchId, long version) {
    Match m = lockAsAdmin(userId, matchId);
    if (isKnockout(m)) throw ApiException.conflict("CUP_MATCH_CANNOT_CANCEL");
    requireVersion(m, version);
    requireScheduled(m);
    m.setStatus(MatchStatus.CANCELLED);
  }

  /**
   * In order, first failure wins: match not SCHEDULED; kickoff still ahead; type not allowed by the sport; a card or MOTM
   * without a player, or a bad assist; a player or assister not in the lineup on {@code side}; a second MOTM.
   * {@code editing}: the event being replaced, which doesn't count as an existing MOTM.
   */
  private void validateEvent(Match m, EventRequest req, UUID editing) {
    requireScheduled(m);
    requireStarted(m);
    requireTeamsSet(m);
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

  /** Puts the user's own player on {@code side} of a public pickup match. */
  @Transactional
  public void join(UUID userId, UUID matchId, Side side) {
    Match m = lockAsReader(userId, matchId);
    if (m.getStageId() != null || m.getHomeTeamId() != null || m.getVisibility() != Visibility.PUBLIC) {
      throw ApiException.conflict("NOT_JOINABLE");
    }
    requireScheduled(m);
    UUID player = players.ownPlayer(userId).getId();
    List<MatchLineup> lineup = lineups.findByMatchId(matchId);
    if (lineup.stream().anyMatch(l -> l.getPlayerId().equals(player))) throw ApiException.conflict("ALREADY_JOINED");
    if (m.getMaxPerSide() != null && lineup.stream().filter(l -> l.getSide() == side).count() >= m.getMaxPerSide()) {
      throw ApiException.conflict("SIDE_FULL");
    }
    lineups.save(new MatchLineup(matchId, player, side, false));
    matches.bumpVersion(matchId);
  }

  /**
   * "I'm in / I'm out" for a scheduled team match, from a player on either team's roster (home if on both).
   * It doesn't change the match itself, so its version stays.
   */
  @Transactional
  public void rsvp(UUID userId, UUID matchId, boolean going) {
    Match m = lockAsReader(userId, matchId);
    requireScheduled(m);
    UUID player = players.ownPlayer(userId).getId();
    Side side = m.getHomeTeamId() != null && teams.rosterIds(m.getHomeTeamId()).contains(player) ? Side.HOME
        : m.getAwayTeamId() != null && teams.rosterIds(m.getAwayTeamId()).contains(player) ? Side.AWAY : null;
    if (side == null) throw ApiException.forbidden();
    MatchRsvp r = rsvps.findByMatchIdAndPlayerId(matchId, player).orElseGet(() -> new MatchRsvp(matchId, player));
    r.answer(side, going);
    rsvps.save(r);
  }

  /** Takes the user's own player out of the lineup, unless an event names them. */
  @Transactional
  public void leave(UUID userId, UUID matchId) {
    Match m = lockAsReader(userId, matchId);
    requireScheduled(m);
    UUID player = players.ownPlayer(userId).getId();
    MatchLineup entry = lineups.findByMatchIdAndPlayerId(matchId, player).orElseThrow(ApiException::notFound);
    if (events.findByMatchId(matchId).stream().anyMatch(e -> player.equals(e.getPlayerId()) || player.equals(e.getAssistPlayerId()))) {
      throw ApiException.conflict("PLAYER_HAS_EVENTS");
    }
    lineups.delete(entry);
    matches.bumpVersion(matchId);
  }

  /**
   * Upcoming public pickups the user isn't part of yet and with room on a side, soonest first; {@code q} matches
   * part of a side's name or the venue, ignoring case and accents (the columns' collation).
   */
  @Transactional(readOnly = true)
  public OpenMatches open(UUID userId, String q, int page) {
    String like = Paging.contains(q);
    String open = """
        FROM sport_match m
        WHERE m.stage_id IS NULL AND m.home_team_id IS NULL AND m.visibility = 'PUBLIC'
          AND m.status = 'SCHEDULED' AND m.kickoff_at > UTC_TIMESTAMP(6)
          AND (m.home_label LIKE :q OR m.away_label LIKE :q OR m.venue LIKE :q)
          AND (m.max_per_side IS NULL
               OR (SELECT COUNT(*) FROM match_lineup l WHERE l.match_id = m.id AND l.side = 'HOME') < m.max_per_side
               OR (SELECT COUNT(*) FROM match_lineup l WHERE l.match_id = m.id AND l.side = 'AWAY') < m.max_per_side)
          AND NOT EXISTS (SELECT 1 FROM admin_grant g
                          WHERE g.resource_type = 'MATCH' AND g.resource_id = m.id AND g.user_id = UUID_TO_BIN(:u))
          AND NOT EXISTS (SELECT 1 FROM match_lineup l JOIN player p ON p.id = l.player_id
                          WHERE l.match_id = m.id AND p.user_id = UUID_TO_BIN(:u))
        """; // ponytail: LIKE scans every open pickup, a FULLTEXT index when there are many thousands
    int total = jdbc.sql("SELECT COUNT(*) " + open).param("q", like).param("u", userId.toString()).query(Integer.class).single();
    Paging paging = Paging.of(total, page);
    List<UUID> ids = jdbc.sql("SELECT BIN_TO_UUID(m.id) " + open + "ORDER BY m.kickoff_at, m.created_at, m.id LIMIT :size OFFSET :offset")
        .param("q", like).param("u", userId.toString())
        .param("size", paging.size()).param("offset", paging.offset())
        .query(UUID.class).list();
    return new OpenMatches(views.summaries(ids), paging.page(), paging.totalPages());
  }

  private MatchEvent eventOf(UUID matchId, UUID eventId) {
    return events.findById(eventId).filter(e -> e.getMatchId().equals(matchId)).orElseThrow(ApiException::notFound);
  }

  /**
   * Matches I hold a grant for or play in, competition matches of competitions I admin, and team matches of
   * teams I play for or admin: unscheduled first, then newest kickoff, then newest created.
   */
  @Transactional(readOnly = true)
  public List<MatchSummary> mine(UUID userId) {
    List<UUID> ids = jdbc.sql("""
            SELECT BIN_TO_UUID(m.id) FROM sport_match m
            WHERE EXISTS (SELECT 1 FROM admin_grant g
                          WHERE g.resource_type = 'MATCH' AND g.resource_id = m.id AND g.user_id = UUID_TO_BIN(:u))
               OR EXISTS (SELECT 1 FROM match_lineup l JOIN player p ON p.id = l.player_id
                          WHERE l.match_id = m.id AND p.user_id = UUID_TO_BIN(:u))
               OR EXISTS (SELECT 1 FROM stage s JOIN admin_grant g ON g.resource_type = 'COMPETITION' AND g.resource_id = s.competition_id
                          WHERE s.id = m.stage_id AND g.user_id = UUID_TO_BIN(:u))
               OR EXISTS (SELECT 1 FROM team_player tp JOIN player p ON p.id = tp.player_id
                          WHERE tp.team_id IN (m.home_team_id, m.away_team_id) AND p.user_id = UUID_TO_BIN(:u))
               OR EXISTS (SELECT 1 FROM admin_grant g WHERE g.resource_type = 'TEAM'
                          AND g.resource_id IN (m.home_team_id, m.away_team_id) AND g.user_id = UUID_TO_BIN(:u))
            ORDER BY m.kickoff_at IS NULL DESC, m.kickoff_at DESC, m.created_at DESC
            """)
        .param("u", userId.toString())
        .query(UUID.class).list();
    return views.summaries(ids);
  }

  /**
   * Lock first, then read anything. At REPEATABLE READ the transaction's snapshot is fixed by its first plain
   * read; taking it after the lock means a write that waited sees what the previous writer committed.
   */
  private Match lockAsAdmin(UUID userId, UUID matchId) {
    Match m = matches.lockForUpdate(matchId).orElseThrow(ApiException::notFound);
    access.requireAdmin(userId, ResourceType.MATCH, matchId);
    return m;
  }

  /** Like {@link #lockAsAdmin}, for writes any reader may make (join, leave). */
  private Match lockAsReader(UUID userId, UUID matchId) {
    Match m = matches.lockForUpdate(matchId).orElseThrow(ApiException::notFound);
    access.requireRead(userId, ResourceType.MATCH, matchId);
    return m;
  }

  /** Cup matches are the ones with a bracket position. */
  private static boolean isKnockout(Match m) {
    return m.getBracketPosition() != null;
  }

  /** A knockout match waits for earlier rounds to fill both sides. */
  private static void requireTeamsSet(Match m) {
    if (isKnockout(m) && (m.getHomeTeamId() == null || m.getAwayTeamId() == null)) throw ApiException.conflict("TEAMS_NOT_SET");
  }

  /** Lock order is always this match, then its next one; nothing locks a later round first, so no deadlock. */
  private Match lockNext(Match m) {
    return matches.lockForUpdate(m.getNextMatchId()).orElseThrow(ApiException::notFound);
  }

  private void requireFuture(Instant kickoff) {
    if (kickoff != null && kickoff.isBefore(clock.instant())) throw ApiException.badRequest("KICKOFF_IN_PAST");
  }

  /** Events and finishing wait for kickoff; a match without one (e.g. a competition fixture) counts as started. */
  private void requireStarted(Match m) {
    if (m.getKickoffAt() != null && m.getKickoffAt().isAfter(clock.instant())) throw ApiException.conflict("NOT_STARTED");
  }

  private static void requireVersion(Match m, long version) {
    if (m.getVersion() != version) throw ApiException.conflict("STALE");
  }

  /** Lineup and events can change only while the match is SCHEDULED; a finished one must be reopened first. */
  private static void requireScheduled(Match m) {
    if (m.getStatus() != MatchStatus.SCHEDULED) throw ApiException.conflict("MATCH_FINISHED");
  }
}
