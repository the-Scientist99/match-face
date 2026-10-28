package com.sportsapp.team;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.ApiException;
import com.sportsapp.player.Player;
import com.sportsapp.player.PlayerService;
import com.sportsapp.sport.Sport;
import com.sportsapp.team.TeamDtos.RosterPlayer;
import com.sportsapp.team.TeamDtos.TeamResponse;
import com.sportsapp.team.TeamDtos.TeamSummary;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

  private final TeamRepository teams;
  private final TeamPlayerRepository roster;
  private final PlayerService players;
  private final AccessService access;
  private final JdbcClient jdbc;

  public TeamService(TeamRepository teams, TeamPlayerRepository roster, PlayerService players, AccessService access, JdbcClient jdbc) {
    this.teams = teams;
    this.roster = roster;
    this.players = players;
    this.access = access;
    this.jdbc = jdbc;
  }

  @Transactional
  public TeamResponse create(UUID userId, String name) {
    Team team = teams.save(new Team(name, Sport.FOOTBALL));
    access.grantOwner(userId, ResourceType.TEAM, team.getId());
    return response(userId, team);
  }

  @Transactional(readOnly = true)
  public TeamResponse get(@Nullable UUID viewerId, UUID teamId) {
    access.requireRead(viewerId, ResourceType.TEAM, teamId);
    return response(viewerId, getRequired(teamId));
  }

  @Transactional
  public TeamResponse rename(UUID userId, UUID teamId, String name) {
    access.requireAdmin(userId, ResourceType.TEAM, teamId);
    Team team = getRequired(teamId);
    team.setName(name);
    return response(userId, team);
  }

  /** Replaces the roster. Every player must be one of the admin's players ("my players"). */
  @Transactional
  public TeamResponse setRoster(UUID userId, UUID teamId, List<UUID> playerIds) {
    access.requireAdmin(userId, ResourceType.TEAM, teamId);
    Set<UUID> wanted = new LinkedHashSet<>(playerIds);
    if (!players.myPlayerIds(userId).containsAll(wanted)) throw ApiException.badRequest("PLAYER_NOT_AVAILABLE");
    roster.deleteByTeamId(teamId);
    roster.saveAll(wanted.stream().map(p -> new TeamPlayer(teamId, p)).toList());
    return response(userId, getRequired(teamId));
  }

  /** Teams I admin or play for, by name. */
  @Transactional(readOnly = true)
  public List<TeamSummary> mine(UUID userId) {
    return jdbc.sql("""
            SELECT BIN_TO_UUID(t.id) id, t.name FROM team t
            WHERE EXISTS (SELECT 1 FROM admin_grant g
                          WHERE g.resource_type = 'TEAM' AND g.resource_id = t.id AND g.user_id = UUID_TO_BIN(:u))
               OR EXISTS (SELECT 1 FROM team_player tp JOIN player p ON p.id = tp.player_id
                          WHERE tp.team_id = t.id AND p.user_id = UUID_TO_BIN(:u))
            ORDER BY t.name
            """)
        .param("u", userId.toString())
        .query((rs, i) -> new TeamSummary(UUID.fromString(rs.getString("id")), rs.getString("name")))
        .list();
  }

  @Transactional(readOnly = true)
  public Set<UUID> rosterIds(UUID teamId) {
    return roster.findByTeamId(teamId).stream().map(TeamPlayer::getPlayerId).collect(Collectors.toSet());
  }

  /** Idempotent. */
  @Transactional
  public void addToRoster(UUID teamId, UUID playerId) {
    if (!roster.existsByTeamIdAndPlayerId(teamId, playerId)) roster.save(new TeamPlayer(teamId, playerId));
  }

  @Transactional(readOnly = true)
  public String name(UUID teamId) {
    return getRequired(teamId).getName();
  }

  private Team getRequired(UUID teamId) {
    return teams.findById(teamId).orElseThrow(ApiException::notFound);
  }

  private TeamResponse response(@Nullable UUID viewerId, Team team) {
    List<RosterPlayer> rosterPlayers = players.getAll(rosterIds(team.getId())).stream()
        .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
        .map(p -> new RosterPlayer(p.getId(), p.getName(), p.getUserId() != null))
        .toList();
    return new TeamResponse(team.getId(), team.getName(), team.getSport(), rosterPlayers,
        access.canAdmin(viewerId, ResourceType.TEAM, team.getId()));
  }
}
