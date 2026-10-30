package com.sportsapp.competition;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.ApiException;
import com.sportsapp.common.Names;
import com.sportsapp.common.Visibility;
import com.sportsapp.competition.CompetitionDtos.AddTeamRequest;
import com.sportsapp.competition.CompetitionDtos.ApproveEntryRequest;
import com.sportsapp.competition.CompetitionDtos.CompetitionEntryView;
import com.sportsapp.competition.CompetitionDtos.CompetitionResponse;
import com.sportsapp.competition.CompetitionDtos.CompetitionSummary;
import com.sportsapp.competition.CompetitionDtos.CompetitionTeamView;
import com.sportsapp.competition.CompetitionDtos.CreateCompetitionRequest;
import com.sportsapp.competition.CompetitionDtos.PatchCompetitionRequest;
import com.sportsapp.image.ImageStore;
import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.competition.CompetitionDtos.BracketMatch;
import com.sportsapp.competition.CompetitionDtos.BracketResponse;
import com.sportsapp.match.MatchService;
import com.sportsapp.match.MatchStatus;
import com.sportsapp.match.MatchViews;
import com.sportsapp.match.Side;
import com.sportsapp.match.ScoreSql;
import com.sportsapp.sport.Sport;
import com.sportsapp.sport.SportRules;
import com.sportsapp.team.TeamService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Competition writes. Each takes the competition row lock first (before any other read: the snapshot is
 * fixed by the first plain read), then checks access, then validates.
 */
@Service
public class CompetitionService {

  private final CompetitionRepository competitions;
  private final CompetitionTeamRepository competitionTeams;
  private final CompetitionEntryRepository entries;
  private final FixtureService fixtures;
  private final AccessService access;
  private final TeamService teams;
  private final MatchViews matchViews;
  private final JdbcClient jdbc;
  private final Clock clock;

  public CompetitionService(CompetitionRepository competitions, CompetitionTeamRepository competitionTeams,
      CompetitionEntryRepository entries, FixtureService fixtures, AccessService access, TeamService teams, MatchViews matchViews, JdbcClient jdbc, Clock clock) {
    this.clock = clock;
    this.matchViews = matchViews;
    this.competitions = competitions;
    this.competitionTeams = competitionTeams;
    this.entries = entries;
    this.fixtures = fixtures;
    this.access = access;
    this.teams = teams;
    this.jdbc = jdbc;
  }

  @Transactional
  public UUID create(UUID userId, CreateCompetitionRequest req) {
    if (req.entriesOpen() && req.visibility() != Visibility.PUBLIC) throw ApiException.badRequest("ENTRIES_NEED_PUBLIC");
    Competition c = new Competition(req.name(), Sport.FOOTBALL, req.type(), req.visibility(), req.doubleRoundRobin());
    c.setEntriesOpen(req.entriesOpen());
    competitions.save(c);
    access.grantOwner(userId, ResourceType.COMPETITION, c.getId());
    return c.getId();
  }

  @Transactional(readOnly = true)
  public CompetitionResponse get(@Nullable UUID viewerId, UUID competitionId) {
    access.requireRead(viewerId, ResourceType.COMPETITION, competitionId);
    return response(viewerId, competitions.findById(competitionId).orElseThrow(ApiException::notFound));
  }

  /** The competition, for reads by other parts of this module (standings, stats); callers check access. */
  @Transactional(readOnly = true)
  public Competition getRequired(UUID competitionId) {
    return competitions.findById(competitionId).orElseThrow(ApiException::notFound);
  }

  @Transactional
  public void patch(UUID userId, UUID competitionId, PatchCompetitionRequest req) {
    Competition c = lockAsAdmin(userId, competitionId);
    if (c.getVersion() != req.version()) throw ApiException.conflict("STALE");
    if (req.doubleRoundRobin() != null && req.doubleRoundRobin() != c.isDoubleRoundRobin()) {
      if (c.getStatus() != CompetitionStatus.DRAFT) throw ApiException.conflict("COMPETITION_STARTED");
      c.setDoubleRoundRobin(req.doubleRoundRobin());
    }
    if (req.name() != null && !req.name().isEmpty()) c.setName(req.name());
    if (req.visibility() != null) {
      c.setVisibility(req.visibility());
      if (req.visibility() == Visibility.PRIVATE) closeEntries(c);
    }
    if (req.entriesOpen() != null) {
      if (req.entriesOpen() && c.getVisibility() != Visibility.PUBLIC) throw ApiException.badRequest("ENTRIES_NEED_PUBLIC");
      if (req.entriesOpen() && c.getStatus() != CompetitionStatus.DRAFT) throw ApiException.conflict("COMPETITION_STARTED");
      c.setEntriesOpen(req.entriesOpen());
    }
  }

  /** Allowed until a match has a result; an active competition's fixtures are then regenerated. */
  @Transactional
  public void addTeam(UUID userId, UUID competitionId, AddTeamRequest req) {
    Competition c = lockAsAdmin(userId, competitionId);
    access.requireAdmin(userId, ResourceType.TEAM, req.teamId());
    enrol(c, req.teamId(), req.seed());
  }

  private void enrol(Competition c, UUID teamId, Integer seed) {
    requireTeamsChangeable(c);
    List<CompetitionTeam> current = competitionTeams.findByCompetitionId(c.getId());
    if (current.stream().anyMatch(ct -> ct.getTeamId().equals(teamId))) throw ApiException.conflict("TEAM_ALREADY_ADDED");
    if (seed != null && current.stream().anyMatch(ct -> seed.equals(ct.getSeed()))) throw ApiException.conflict("DUPLICATE_SEED");
    competitionTeams.save(new CompetitionTeam(c.getId(), teamId, seed));
    entries.deleteByCompetitionIdAndTeamId(c.getId(), teamId);
    if (c.getStatus() == CompetitionStatus.ACTIVE) fixtures.generate(c);
  }

  /** A team's admin asks to join a public competition with open entries; the organizer decides. */
  @Transactional
  public void enter(UUID userId, UUID competitionId, UUID teamId) {
    Competition c = competitions.lockForUpdate(competitionId).orElseThrow(ApiException::notFound);
    access.requireRead(userId, ResourceType.COMPETITION, competitionId);
    access.requireAdmin(userId, ResourceType.TEAM, teamId);
    if (!c.isEntriesOpen()) throw ApiException.conflict("ENTRIES_CLOSED");
    if (competitionTeams.findByCompetitionId(competitionId).stream().anyMatch(ct -> ct.getTeamId().equals(teamId))) {
      throw ApiException.conflict("TEAM_ALREADY_ADDED");
    }
    if (entries.findByCompetitionIdAndTeamId(competitionId, teamId).isPresent()) throw ApiException.conflict("ENTRY_EXISTS");
    entries.save(new CompetitionEntry(competitionId, teamId));
  }

  /** Requests exist only in a draft: generating drops them, so this never touches fixtures. */
  @Transactional
  public void approveEntry(UUID userId, UUID competitionId, UUID teamId, ApproveEntryRequest req) {
    Competition c = lockAsAdmin(userId, competitionId);
    entries.findByCompetitionIdAndTeamId(competitionId, teamId).orElseThrow(ApiException::notFound);
    enrol(c, teamId, req.seed());
  }

  /** The organizer rejects a request, or the team's admin withdraws it. */
  @Transactional
  public void removeEntry(UUID userId, UUID competitionId, UUID teamId) {
    competitions.lockForUpdate(competitionId).orElseThrow(ApiException::notFound);
    if (!access.canAdmin(userId, ResourceType.TEAM, teamId)) access.requireAdmin(userId, ResourceType.COMPETITION, competitionId);
    entries.delete(entries.findByCompetitionIdAndTeamId(competitionId, teamId).orElseThrow(ApiException::notFound));
  }

  private void closeEntries(Competition c) {
    c.setEntriesOpen(false);
    entries.deleteByCompetitionId(c.getId());
  }

  /** Like adding; an active competition left with fewer than 2 teams loses its fixtures and is a draft again. */
  @Transactional
  public void removeTeam(UUID userId, UUID competitionId, UUID teamId) {
    Competition c = lockAsAdmin(userId, competitionId);
    List<CompetitionTeam> current = competitionTeams.findByCompetitionId(competitionId);
    CompetitionTeam ct = current.stream().filter(x -> x.getTeamId().equals(teamId)).findFirst().orElseThrow(ApiException::notFound);
    requireTeamsChangeable(c);
    competitionTeams.delete(ct);
    competitionTeams.flush();
    if (c.getStatus() != CompetitionStatus.ACTIVE) return;
    if (current.size() - 1 < 2) {
      fixtures.deleteFixtures(c);
      c.setStatus(CompetitionStatus.DRAFT);
    } else {
      fixtures.generate(c);
    }
  }

  /** (Re)generates every fixture and makes the competition ACTIVE. */
  /**
   * One kickoff for every still-scheduled match of a round, e.g. a weekly league's matchday. Bumps their
   * versions, so an open match page gets STALE on its next detail edit. Lock order competition → match.
   */
  @Transactional
  public void setRoundKickoff(UUID userId, UUID competitionId, int round, Instant kickoffAt) {
    lockAsAdmin(userId, competitionId);
    if (kickoffAt.isBefore(clock.instant())) throw ApiException.badRequest("KICKOFF_IN_PAST");
    int updated = jdbc.sql("""
            UPDATE sport_match m JOIN stage s ON s.id = m.stage_id
            SET m.kickoff_at = :k, m.version = m.version + 1
            WHERE s.competition_id = UUID_TO_BIN(:c) AND m.round = :r AND m.status = 'SCHEDULED'
            """)
        .param("k", LocalDateTime.ofInstant(kickoffAt, ZoneOffset.UTC)) // stored as UTC, like Hibernate does
        .param("c", competitionId.toString()).param("r", round).update();
    if (updated == 0) throw ApiException.notFound();
  }

  @Transactional
  public void generate(UUID userId, UUID competitionId) {
    Competition c = lockAsAdmin(userId, competitionId);
    if (competitionTeams.findByCompetitionId(competitionId).size() < 2) throw ApiException.conflict("NOT_ENOUGH_TEAMS");
    requireTeamsChangeable(c);
    fixtures.generate(c);
    c.setStatus(CompetitionStatus.ACTIVE);
    closeEntries(c);
  }

  /** By round, then kickoff (none last), then creation. */
  @Transactional(readOnly = true)
  public List<MatchSummary> matches(@Nullable UUID viewerId, UUID competitionId) {
    access.requireRead(viewerId, ResourceType.COMPETITION, competitionId);
    List<UUID> ids = jdbc.sql("""
            SELECT BIN_TO_UUID(m.id) FROM sport_match m JOIN stage s ON s.id = m.stage_id
            WHERE s.competition_id = UUID_TO_BIN(:c)
            ORDER BY m.round, m.kickoff_at IS NULL, m.kickoff_at, m.created_at, m.id
            """)
        .param("c", competitionId.toString()).query(UUID.class).list();
    return matchViews.summaries(ids);
  }

  private record BracketRow(UUID id, int round, int position, Side penaltyWinner, UUID home, UUID away) {}

  /** A cup's bracket, with its winner once the final is finished. A league has none: 400 NOT_A_CUP. */
  @Transactional(readOnly = true)
  public BracketResponse bracket(@Nullable UUID viewerId, UUID competitionId) {
    access.requireRead(viewerId, ResourceType.COMPETITION, competitionId);
    if (getRequired(competitionId).getType() != CompetitionType.CUP) throw ApiException.badRequest("NOT_A_CUP");
    List<BracketRow> rows = jdbc.sql("""
            SELECT BIN_TO_UUID(m.id) id, m.round, m.bracket_position, m.penalty_winner_side,
                   BIN_TO_UUID(m.home_team_id) home, BIN_TO_UUID(m.away_team_id) away
            FROM sport_match m JOIN stage s ON s.id = m.stage_id
            WHERE s.competition_id = UUID_TO_BIN(:c) AND s.type = 'KNOCKOUT'
            ORDER BY m.round, m.bracket_position
            """)
        .param("c", competitionId.toString())
        .query((rs, i) -> new BracketRow(UUID.fromString(rs.getString("id")), rs.getInt("round"), rs.getInt("bracket_position"),
            rs.getString("penalty_winner_side") == null ? null : Side.valueOf(rs.getString("penalty_winner_side")),
            rs.getString("home") == null ? null : UUID.fromString(rs.getString("home")),
            rs.getString("away") == null ? null : UUID.fromString(rs.getString("away"))))
        .list();
    Map<UUID, MatchSummary> summaries = matchViews.summaries(rows.stream().map(BracketRow::id).toList()).stream()
        .collect(Collectors.toMap(MatchSummary::id, Function.identity()));
    List<BracketMatch> matches = rows.stream()
        .map(r -> new BracketMatch(summaries.get(r.id()), r.round(), r.position(), r.penaltyWinner())).toList();
    int rounds = rows.stream().mapToInt(BracketRow::round).max().orElse(0);
    TeamRef winner = null;
    BracketRow fin = rows.isEmpty() ? null : rows.getLast();
    if (fin != null && summaries.get(fin.id()).status() == MatchStatus.FINISHED) {
      Side side = MatchService.winnerSide(summaries.get(fin.id()).score(), fin.penaltyWinner());
      UUID team = side == Side.HOME ? fin.home() : fin.away();
      winner = new TeamRef(team, teams.name(team));
    }
    return new BracketResponse(rounds, matches, winner);
  }

  /** The league table from finished round-robin matches. A cup has none: 400 NOT_A_LEAGUE. */
  @Transactional(readOnly = true)
  public List<StandingRow> standings(@Nullable UUID viewerId, UUID competitionId) {
    access.requireRead(viewerId, ResourceType.COMPETITION, competitionId);
    Competition c = getRequired(competitionId);
    if (c.getType() != CompetitionType.LEAGUE) throw ApiException.badRequest("NOT_A_LEAGUE");
    List<TeamRef> teamRefs = competitionTeams.findByCompetitionId(competitionId).stream()
        .map(ct -> new TeamRef(ct.getTeamId(), teams.name(ct.getTeamId()))).toList();
    List<Result> results = jdbc.sql("""
            SELECT BIN_TO_UUID(m.home_team_id) home, BIN_TO_UUID(m.away_team_id) away, %s home_goals, %s away_goals
            FROM sport_match m JOIN stage s ON s.id = m.stage_id
            LEFT JOIN match_event e ON e.match_id = m.id
            WHERE s.competition_id = UUID_TO_BIN(:c) AND s.type = 'ROUND_ROBIN' AND m.status = 'FINISHED'
            GROUP BY m.id
            """.formatted(ScoreSql.HOME_GOALS, ScoreSql.AWAY_GOALS))
        .param("c", competitionId.toString())
        .query((rs, i) -> new Result(UUID.fromString(rs.getString("home")), UUID.fromString(rs.getString("away")),
            rs.getInt("home_goals"), rs.getInt("away_goals")))
        .list();
    return Standings.compute(teamRefs, results, SportRules.of(c.getSport()));
  }

  /** Competitions I admin, play in (my player on a team's roster) or have a team I admin in, by name. */
  @Transactional(readOnly = true)
  public List<CompetitionSummary> mine(UUID userId) {
    return jdbc.sql("""
            SELECT BIN_TO_UUID(c.id) id, c.name, c.type, c.status, c.image FROM competition c
            WHERE EXISTS (SELECT 1 FROM admin_grant g
                          WHERE g.resource_type = 'COMPETITION' AND g.resource_id = c.id AND g.user_id = UUID_TO_BIN(:u))
               OR EXISTS (SELECT 1 FROM competition_team ct JOIN team_player tp ON tp.team_id = ct.team_id
                          JOIN player p ON p.id = tp.player_id WHERE ct.competition_id = c.id AND p.user_id = UUID_TO_BIN(:u))
               OR EXISTS (SELECT 1 FROM competition_team ct JOIN admin_grant g ON g.resource_type = 'TEAM' AND g.resource_id = ct.team_id
                          WHERE ct.competition_id = c.id AND g.user_id = UUID_TO_BIN(:u))
            """)
        .param("u", userId.toString())
        .query((rs, i) -> new CompetitionSummary(UUID.fromString(rs.getString("id")), rs.getString("name"),
            CompetitionType.valueOf(rs.getString("type")), CompetitionStatus.valueOf(rs.getString("status")),
            ImageStore.url(rs.getString("image"))))
        .list().stream()
        .sorted(Comparator.comparing(CompetitionSummary::name, Names.ORDER))
        .toList();
  }

  private Competition lockAsAdmin(UUID userId, UUID competitionId) {
    Competition c = competitions.lockForUpdate(competitionId).orElseThrow(ApiException::notFound);
    access.requireAdmin(userId, ResourceType.COMPETITION, competitionId);
    return c;
  }

  /** Teams can change, and fixtures be regenerated, until any match has a result. */
  private void requireTeamsChangeable(Competition c) {
    if (hasFinishedMatch(c.getId())) throw ApiException.conflict("TEAMS_LOCKED");
  }

  /**
   * A locking read of the competition's matches: a Finish in flight locks only its match row, so a plain
   * (snapshot) read would miss it and the regeneration that follows would delete the new result. FOR UPDATE
   * waits for that Finish and sees it; a later Finish waits for us. Lock order is competition → match, and
   * match writes never take the competition lock, so this can't deadlock with them.
   */
  private boolean hasFinishedMatch(UUID competitionId) {
    return jdbc.sql("""
            SELECT m.status FROM sport_match m JOIN stage s ON s.id = m.stage_id
            WHERE s.competition_id = UUID_TO_BIN(:c) FOR UPDATE
            """)
        .param("c", competitionId.toString()).query(String.class).list().contains("FINISHED");
  }

  private CompetitionResponse response(@Nullable UUID viewerId, Competition c) {
    List<CompetitionTeamView> teamViews = competitionTeams.findByCompetitionId(c.getId()).stream()
        .map(ct -> new CompetitionTeamView(ct.getTeamId(), teams.name(ct.getTeamId()), ct.getSeed()))
        .sorted(Comparator.comparing(CompetitionTeamView::seed, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CompetitionTeamView::name, Names.ORDER))
        .toList();
    var counts = jdbc.sql("""
            SELECT COUNT(*) total, COALESCE(SUM(m.status = 'SCHEDULED'), 0) open
            FROM sport_match m JOIN stage s ON s.id = m.stage_id WHERE s.competition_id = UUID_TO_BIN(:c)
            """)
        .param("c", c.getId().toString())
        .query((rs, i) -> new int[] {rs.getInt("total"), rs.getInt("open")}).single();
    boolean completed = counts[0] > 0 && counts[1] == 0;
    boolean canAdmin = access.canAdmin(viewerId, ResourceType.COMPETITION, c.getId());
    List<CompetitionEntryView> entryViews = entries.findByCompetitionId(c.getId()).stream()
        .filter(e -> canAdmin || access.canAdmin(viewerId, ResourceType.TEAM, e.getTeamId()))
        .map(e -> new CompetitionEntryView(e.getTeamId(), teams.name(e.getTeamId())))
        .sorted(Comparator.comparing(CompetitionEntryView::name, Names.ORDER))
        .toList();
    return new CompetitionResponse(c.getId(), c.getVersion(), c.getName(), c.getSport(), c.getType(), c.getVisibility(), c.getStatus(),
        c.isDoubleRoundRobin(), teamViews, canAdmin, completed, ImageStore.url(c.getImage()), c.isEntriesOpen(), entryViews);
  }
}
