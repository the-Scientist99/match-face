package com.sportsapp.competition;

import static com.sportsapp.common.Visibility.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class CompetitionApiTest extends IntegrationTest {

  private TestUser ana;
  private UUID alpha;
  private UUID beta;
  private UUID gamma;
  private UUID delta;

  @BeforeEach
  void setUpTeams() throws Exception {
    ana = register("Ana");
    alpha = createTeam(ana, "Alpha");
    beta = createTeam(ana, "Beta");
    gamma = createTeam(ana, "Gamma");
    delta = createTeam(ana, "Delta");
  }

  private UUID createDraft(boolean doubleRound) throws Exception {
    String body = mvc.perform(post("/api/competitions").with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("name", "League", "type", "LEAGUE", "visibility", "PRIVATE", "doubleRoundRobin", doubleRound))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asString());
  }

  private ResultActions addTeam(UUID competition, UUID team, Integer seed) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("teamId", team);
    if (seed != null) body.put("seed", seed);
    return mvc.perform(post("/api/competitions/{id}/teams", competition).with(as(ana)).contentType(APPLICATION_JSON).content(toJson(body)));
  }

  private ResultActions generate(UUID competition) throws Exception {
    return mvc.perform(post("/api/competitions/{id}/generate", competition).with(as(ana)));
  }

  private int rounds(UUID competition) {
    return jdbc.sql("SELECT COUNT(DISTINCT m.round) FROM sport_match m JOIN stage s ON s.id = m.stage_id WHERE s.competition_id = UUID_TO_BIN(:c)")
        .param("c", competition.toString()).query(Integer.class).single();
  }

  @Test
  void createLeagueIsDraft() throws Exception {
    mvc.perform(post("/api/competitions").with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"name\":\" Liga \",\"type\":\"LEAGUE\",\"visibility\":\"PRIVATE\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Liga"))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.type").value("LEAGUE"))
        .andExpect(jsonPath("$.canAdmin").value(true))
        .andExpect(jsonPath("$.completed").value(false))
        .andExpect(jsonPath("$.teams").value(empty()));
    mvc.perform(post("/api/competitions").contentType(APPLICATION_JSON)
            .content("{\"name\":\"Liga\",\"type\":\"LEAGUE\",\"visibility\":\"PRIVATE\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void generateFourTeamLeague() throws Exception {
    UUID c = createDraft(false);
    for (UUID t : List.of(alpha, beta, gamma, delta)) addTeam(c, t, null).andExpect(status().isOk());
    generate(c).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.teams.length()").value(4));
    assertThat(competitionMatches(c)).hasSize(6);
    assertThat(rounds(c)).isEqualTo(3);
    assertThat(jdbc.sql("SELECT DISTINCT status FROM sport_match").query(String.class).list()).containsExactly("SCHEDULED");
  }

  @Test
  void generateDoubleRound() throws Exception {
    UUID c = createDraft(true);
    for (UUID t : List.of(alpha, beta, gamma)) addTeam(c, t, null);
    generate(c).andExpect(status().isOk());
    assertThat(competitionMatches(c)).hasSize(6);
    assertThat(rounds(c)).isEqualTo(6);
  }

  @Test
  void notEnoughTeams() throws Exception {
    UUID c = createDraft(false);
    addTeam(c, alpha, null);
    generate(c).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_ENOUGH_TEAMS"));
  }

  @Test
  void addTeamWhileActiveWithoutResultsRegenerates() throws Exception {
    UUID c = createLeague(ana, PRIVATE, alpha, beta, gamma);
    List<UUID> before = competitionMatches(c);
    assertThat(before).hasSize(3);
    addTeam(c, delta, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    List<UUID> after = competitionMatches(c);
    assertThat(after).hasSize(6).doesNotContainAnyElementsOf(before);
    mvc.perform(delete("/api/competitions/{id}/teams/{t}", c, delta).with(as(ana))).andExpect(status().isOk());
    assertThat(competitionMatches(c)).hasSize(3);
  }

  @Test
  void removingDownToOneTeamReturnsToDraft() throws Exception {
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    mvc.perform(delete("/api/competitions/{id}/teams/{t}", c, beta).with(as(ana)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
    assertThat(competitionMatches(c)).isEmpty();
  }

  @Test
  void teamsLockedAfterAResult() throws Exception {
    UUID c = createLeague(ana, PRIVATE, alpha, beta, gamma);
    finish(ana, competitionMatches(c).getFirst());
    addTeam(c, delta, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAMS_LOCKED"));
    mvc.perform(delete("/api/competitions/{id}/teams/{t}", c, alpha).with(as(ana)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAMS_LOCKED"));
    generate(c).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAMS_LOCKED"));
  }

  @Test
  void duplicateTeamAndSeed() throws Exception {
    UUID c = createDraft(false);
    addTeam(c, alpha, 1).andExpect(status().isOk());
    addTeam(c, alpha, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAM_ALREADY_ADDED"));
    addTeam(c, beta, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SEED"));
    addTeam(c, beta, 65).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.seed").value("Max"));
  }

  @Test
  void cannotAddTeamYouDoNotAdmin() throws Exception {
    TestUser ivan = register("Ivan");
    UUID ivans = createTeam(ivan, "Ivans");
    UUID c = createDraft(false);
    addTeam(c, ivans, null).andExpect(status().isNotFound());
  }

  @Test
  void doubleRoundLockedAfterDraft() throws Exception {
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    long v = json.readTree(mvc.perform(get("/api/competitions/{id}", c).with(as(ana))).andReturn().getResponse().getContentAsString()).get("version").asLong();
    mvc.perform(patch("/api/competitions/{id}", c).with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"version\":" + v + ",\"doubleRoundRobin\":true}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPETITION_STARTED"));
    mvc.perform(patch("/api/competitions/{id}", c).with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"version\":" + v + ",\"name\":\"Renamed\",\"visibility\":\"PUBLIC\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed")).andExpect(jsonPath("$.visibility").value("PUBLIC"));
    mvc.perform(patch("/api/competitions/{id}", c).with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"version\":" + v + ",\"name\":\"Again\"}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE"));
  }

  @Test
  void lineupAutoAddsToRoster() throws Exception {
    UUID guest = createGuest(ana, "Marko");
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    UUID m = competitionMatches(c).getFirst();
    String side = jdbc.sql("SELECT IF(home_team_id = UUID_TO_BIN(:a), 'HOME', 'AWAY') FROM sport_match WHERE id = UUID_TO_BIN(:m)")
        .param("a", alpha.toString()).param("m", m.toString()).query(String.class).single();
    mvc.perform(put("/api/matches/{id}/lineup", m).with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("entries", List.of(Map.of("playerId", guest, "side", side))))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/teams/{id}", alpha).with(as(ana))).andExpect(jsonPath("$.players[*].name").value(contains("Marko")));
  }

  @Test
  void lineupAllowsTheSideTeamsRoster() throws Exception {
    TestUser ivan = register("Ivan");
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    putOnRoster(alpha, ivan.playerId());
    jdbc.sql("DELETE FROM admin_grant WHERE resource_type = 'TEAM'").update(); // ana no longer admins the teams
    UUID m = competitionMatches(c).getFirst();
    String side = jdbc.sql("SELECT IF(home_team_id = UUID_TO_BIN(:a), 'HOME', 'AWAY') FROM sport_match WHERE id = UUID_TO_BIN(:m)")
        .param("a", alpha.toString()).param("m", m.toString()).query(String.class).single();
    String other = side.equals("HOME") ? "AWAY" : "HOME";
    mvc.perform(put("/api/matches/{id}/lineup", m).with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("entries", List.of(Map.of("playerId", ivan.playerId(), "side", other))))))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAYER_NOT_AVAILABLE"));
    mvc.perform(put("/api/matches/{id}/lineup", m).with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("entries", List.of(Map.of("playerId", ivan.playerId(), "side", side))))))
        .andExpect(status().isOk());
  }

  @Test
  void competitionMatchVisibilityPatchRejected() throws Exception {
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    UUID m = competitionMatches(c).getFirst();
    mvc.perform(patch("/api/matches/{id}", m).with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"version\":" + version(m) + ",\"visibility\":\"PUBLIC\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FIELD"));
  }

  @Test
  void matchViewShowsCompetition() throws Exception {
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    UUID m = competitionMatches(c).getFirst();
    mvc.perform(get("/api/matches/{id}", m).with(as(ana)))
        .andExpect(jsonPath("$.competitionId").value(c.toString()))
        .andExpect(jsonPath("$.competitionName").value("League"))
        .andExpect(jsonPath("$.round").value(1))
        .andExpect(jsonPath("$.visibility").value("PRIVATE"))
        .andExpect(jsonPath("$.canAdmin").value(true));
  }

  @Test
  void mineListsMyCompetitions() throws Exception {
    TestUser ivan = register("Ivan");
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    mvc.perform(get("/api/competitions/mine").with(as(ana))).andExpect(jsonPath("$[*].id").value(contains(c.toString())));
    mvc.perform(get("/api/competitions/mine").with(as(ivan))).andExpect(jsonPath("$").value(empty()));
    putOnRoster(beta, ivan.playerId());
    mvc.perform(get("/api/competitions/mine").with(as(ivan))).andExpect(jsonPath("$[0].name").value("League"));
  }

  @Test
  void teamsMineShowsCanAdmin() throws Exception {
    TestUser ivan = register("Ivan");
    putOnRoster(alpha, ivan.playerId());
    createTeam(ivan, "Ivans");
    mvc.perform(get("/api/teams/mine").with(as(ivan)))
        .andExpect(jsonPath("$[*].name").value(contains("Alpha", "Ivans")))
        .andExpect(jsonPath("$[*].canAdmin").value(contains(false, true)));
  }
}
