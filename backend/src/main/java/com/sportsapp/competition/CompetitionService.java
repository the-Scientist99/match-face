package com.sportsapp.competition;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.ApiException;
import com.sportsapp.common.Names;
import com.sportsapp.competition.CompetitionDtos.AddTeamRequest;
import com.sportsapp.competition.CompetitionDtos.CompetitionResponse;
import com.sportsapp.competition.CompetitionDtos.CompetitionSummary;
import com.sportsapp.competition.CompetitionDtos.CompetitionTeamView;
import com.sportsapp.competition.CompetitionDtos.CreateCompetitionRequest;
import com.sportsapp.competition.CompetitionDtos.PatchCompetitionRequest;
import com.sportsapp.sport.Sport;
import com.sportsapp.team.TeamService;
import java.util.Comparator;
import java.util.List;
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
  private final FixtureService fixtures;
  private final AccessService access;
  private final TeamService teams;
  private final JdbcClient jdbc;

  public CompetitionService(CompetitionRepository competitions, CompetitionTeamRepository competitionTeams, FixtureService fixtures,
      AccessService access, TeamService teams, JdbcClient jdbc) {
    this.competitions = competitions;
    this.competitionTeams = competitionTeams;
    this.fixtures = fixtures;
    this.access = access;
    this.teams = teams;
    this.jdbc = jdbc;
  }

  @Transactional
  public UUID create(UUID userId, CreateCompetitionRequest req) {
    Competition c = competitions.save(new Competition(req.name(), Sport.FOOTBALL, req.type(), req.visibility(), req.doubleRoundRobin()));
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
    if (req.visibility() != null) c.setVisibility(req.visibility());
  }

  /** Allowed until a match has a result; an active competition's fixtures are then regenerated. */
  @Transactional
  public void addTeam(UUID userId, UUID competitionId, AddTeamRequest req) {
    Competition c = lockAsAdmin(userId, competitionId);
    access.requireAdmin(userId, ResourceType.TEAM, req.teamId());
    requireTeamsChangeable(c);
    List<CompetitionTeam> current = competitionTeams.findByCompetitionId(competitionId);
    if (current.stream().anyMatch(ct -> ct.getTeamId().equals(req.teamId()))) throw ApiException.conflict("TEAM_ALREADY_ADDED");
    if (req.seed() != null && current.stream().anyMatch(ct -> req.seed().equals(ct.getSeed()))) throw ApiException.conflict("DUPLICATE_SEED");
    competitionTeams.save(new CompetitionTeam(competitionId, req.teamId(), req.seed()));
    if (c.getStatus() == CompetitionStatus.ACTIVE) fixtures.generate(c);
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
  @Transactional
  public void generate(UUID userId, UUID competitionId) {
    Competition c = lockAsAdmin(userId, competitionId);
    if (competitionTeams.findByCompetitionId(competitionId).size() < 2) throw ApiException.conflict("NOT_ENOUGH_TEAMS");
    requireTeamsChangeable(c);
    fixtures.generate(c);
    c.setStatus(CompetitionStatus.ACTIVE);
  }

  /** Competitions I admin or play in (my player on a team's roster), by name. */
  @Transactional(readOnly = true)
  public List<CompetitionSummary> mine(UUID userId) {
    return jdbc.sql("""
            SELECT BIN_TO_UUID(c.id) id, c.name, c.type, c.status FROM competition c
            WHERE EXISTS (SELECT 1 FROM admin_grant g
                          WHERE g.resource_type = 'COMPETITION' AND g.resource_id = c.id AND g.user_id = UUID_TO_BIN(:u))
               OR EXISTS (SELECT 1 FROM competition_team ct JOIN team_player tp ON tp.team_id = ct.team_id
                          JOIN player p ON p.id = tp.player_id WHERE ct.competition_id = c.id AND p.user_id = UUID_TO_BIN(:u))
            """)
        .param("u", userId.toString())
        .query((rs, i) -> new CompetitionSummary(UUID.fromString(rs.getString("id")), rs.getString("name"),
            CompetitionType.valueOf(rs.getString("type")), CompetitionStatus.valueOf(rs.getString("status"))))
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

  private boolean hasFinishedMatch(UUID competitionId) {
    return jdbc.sql("""
            SELECT EXISTS (SELECT 1 FROM sport_match m JOIN stage s ON s.id = m.stage_id
                           WHERE s.competition_id = UUID_TO_BIN(:c) AND m.status = 'FINISHED')
            """)
        .param("c", competitionId.toString()).query(Boolean.class).single();
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
    return new CompetitionResponse(c.getId(), c.getVersion(), c.getName(), c.getSport(), c.getType(), c.getVisibility(), c.getStatus(),
        c.isDoubleRoundRobin(), teamViews, access.canAdmin(viewerId, ResourceType.COMPETITION, c.getId()), completed);
  }
}
