package com.sportsapp.competition;

import static com.sportsapp.common.Visibility.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import com.sportsapp.match.Side;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class CupApiTest extends IntegrationTest {

  private TestUser ana;
  /** t[i] is seed i + 1. */
  private final List<UUID> t = new ArrayList<>();
  /** One player on each team's roster. */
  private final Map<UUID, UUID> playerOf = new HashMap<>();

  @BeforeEach
  void setUpTeams() throws Exception {
    ana = register("Ana");
    for (int i = 1; i <= 6; i++) {
      UUID p = createGuest(ana, "P" + i);
      UUID team = createTeam(ana, "T" + i, p);
      t.add(team);
      playerOf.put(team, p);
    }
  }

  private UUID matchAt(UUID cup, int round, int position) {
    return jdbc.sql("""
            SELECT BIN_TO_UUID(m.id) FROM sport_match m JOIN stage s ON s.id = m.stage_id
            WHERE s.competition_id = UUID_TO_BIN(:c) AND m.round = :r AND m.bracket_position = :p
            """)
        .param("c", cup.toString()).param("r", round).param("p", position).query(UUID.class).single();
  }

  private UUID team(UUID match, Side side) {
    String col = side == Side.HOME ? "home_team_id" : "away_team_id";
    return jdbc.sql("SELECT BIN_TO_UUID(" + col + ") FROM sport_match WHERE id = UUID_TO_BIN(:m)").param("m", match.toString())
        .query(UUID.class).optional().orElse(null);
  }

  private void goals(UUID match, int home, int away) throws Exception {
    for (int i = 0; i < home; i++) addEvent(ana, match, "type", "GOAL", "side", "HOME");
    for (int i = 0; i < away; i++) addEvent(ana, match, "type", "GOAL", "side", "AWAY");
  }

  private ResultActions finish(UUID match, String penaltyWinner) throws Exception {
    Map<String, Object> body = new HashMap<>(Map.of("version", version(match)));
    if (penaltyWinner != null) body.put("penaltyWinner", penaltyWinner);
    return mvc.perform(post("/api/matches/{id}/finish", match).with(as(ana)).contentType(APPLICATION_JSON).content(toJson(body)));
  }

  private ResultActions lifecycle(UUID match, String action) throws Exception {
    return mvc.perform(post("/api/matches/{id}/" + action, match).with(as(ana)).contentType(APPLICATION_JSON)
        .content("{\"version\":" + version(match) + "}"));
  }

  private ResultActions bracket(UUID cup) throws Exception {
    return mvc.perform(get("/api/competitions/{id}/bracket", cup).with(as(ana)));
  }

  private UUID sixTeamCup() throws Exception {
    return createCup(ana, PRIVATE, t.toArray(UUID[]::new));
  }

  @Test
  void generateSixTeamCup() throws Exception {
    UUID cup = sixTeamCup();
    assertThat(competitionMatches(cup)).hasSize(5);
    bracket(cup).andExpect(status().isOk())
        .andExpect(jsonPath("$.rounds").value(3))
        .andExpect(jsonPath("$.matches.length()").value(5))
        .andExpect(jsonPath("$.matches[?(@.round == 1)]").isArray())
        .andExpect(jsonPath("$.matches[0].round").value(1))
        .andExpect(jsonPath("$.matches[0].position").value(1))
        .andExpect(jsonPath("$.matches[1].position").value(3))
        .andExpect(jsonPath("$.matches[2].round").value(2))
        .andExpect(jsonPath("$.matches[2].match.homeName").value("T1"))
        .andExpect(jsonPath("$.matches[3].match.homeName").value("T2"))
        .andExpect(jsonPath("$.winner").doesNotExist());
    assertThat(team(matchAt(cup, 1, 1), Side.HOME)).isEqualTo(t.get(3));
    assertThat(team(matchAt(cup, 1, 1), Side.AWAY)).isEqualTo(t.get(4));
  }

  @Test
  void winnerAdvancesToNextSlot() throws Exception {
    UUID cup = sixTeamCup();
    UUID qf = matchAt(cup, 1, 1); // seed 4 v seed 5
    goals(qf, 1, 0);
    finish(qf, null).andExpect(status().isOk()).andExpect(jsonPath("$.penaltyWinner").doesNotExist());
    assertThat(team(matchAt(cup, 2, 0), Side.AWAY)).isEqualTo(t.get(3));
    UUID other = matchAt(cup, 1, 3); // seed 3 v seed 6, feeds round 2 position 1's AWAY slot
    goals(other, 0, 2);
    finish(other, null).andExpect(status().isOk());
    assertThat(team(matchAt(cup, 2, 1), Side.AWAY)).isEqualTo(t.get(5));
  }

  @Test
  void drawNeedsPenalties() throws Exception {
    UUID cup = sixTeamCup();
    UUID qf = matchAt(cup, 1, 1);
    goals(qf, 1, 1);
    finish(qf, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUP_DRAW_NEEDS_PENALTIES"));
    finish(qf, "AWAY").andExpect(status().isOk()).andExpect(jsonPath("$.penaltyWinner").value("AWAY"));
    assertThat(team(matchAt(cup, 2, 0), Side.AWAY)).isEqualTo(t.get(4));
  }

  @Test
  void penaltyWinnerIgnoredWhenNotLevel() throws Exception {
    UUID cup = sixTeamCup();
    UUID qf = matchAt(cup, 1, 1);
    goals(qf, 2, 1);
    finish(qf, "AWAY").andExpect(status().isOk()).andExpect(jsonPath("$.penaltyWinner").doesNotExist());
    assertThat(team(matchAt(cup, 2, 0), Side.AWAY)).isEqualTo(t.get(3));
  }

  @Test
  void undecidedMatchIsLocked() throws Exception {
    UUID cup = sixTeamCup();
    for (UUID m : List.of(matchAt(cup, 3, 0), matchAt(cup, 2, 0))) {
      mvc.perform(put("/api/matches/{id}/lineup", m).with(as(ana)).contentType(APPLICATION_JSON).content("{\"entries\":[]}"))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAMS_NOT_SET"));
      mvc.perform(post("/api/matches/{id}/events", m).with(as(ana)).contentType(APPLICATION_JSON).content("{\"type\":\"GOAL\",\"side\":\"HOME\"}"))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAMS_NOT_SET"));
      finish(m, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAMS_NOT_SET"));
    }
  }

  @Test
  void reopenClearsNextSlotAndItsLineup() throws Exception {
    UUID cup = sixTeamCup();
    UUID qf = matchAt(cup, 1, 1);
    goals(qf, 1, 0);
    finish(qf, null).andExpect(status().isOk());
    UUID sf = matchAt(cup, 2, 0);
    setLineup(ana, sf, playerOf.get(t.get(0)), Side.HOME, playerOf.get(t.get(3)), Side.AWAY);
    lifecycle(qf, "reopen").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.penaltyWinner").doesNotExist());
    assertThat(team(sf, Side.AWAY)).isNull();
    assertThat(team(sf, Side.HOME)).isEqualTo(t.get(0));
    mvc.perform(get("/api/matches/{id}", sf).with(as(ana)))
        .andExpect(jsonPath("$.lineup.length()").value(1))
        .andExpect(jsonPath("$.lineup[0].side").value("HOME"));
  }

  @Test
  void reopenBlockedWhenNextMatchStarted() throws Exception {
    UUID cup = sixTeamCup();
    UUID qf = matchAt(cup, 1, 1);
    goals(qf, 1, 0);
    finish(qf, null);
    UUID sf = matchAt(cup, 2, 0);
    addEvent(ana, sf, "type", "GOAL", "side", "HOME");
    lifecycle(qf, "reopen").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LATER_ROUND_FINISHED"));
    finish(sf, null).andExpect(status().isOk());
    lifecycle(qf, "reopen").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LATER_ROUND_FINISHED"));
  }

  @Test
  void cupMatchCannotBeCancelled() throws Exception {
    UUID cup = sixTeamCup();
    lifecycle(matchAt(cup, 1, 1), "cancel").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUP_MATCH_CANNOT_CANCEL"));
  }

  @Test
  void finalDecidesWinner() throws Exception {
    UUID cup = createCup(ana, PRIVATE, t.get(0), t.get(1));
    UUID fin = matchAt(cup, 1, 0);
    goals(fin, 3, 0);
    finish(fin, null).andExpect(status().isOk());
    bracket(cup).andExpect(jsonPath("$.winner.id").value(t.get(0).toString())).andExpect(jsonPath("$.winner.name").value("T1"));
    mvc.perform(get("/api/competitions/{id}", cup).with(as(ana))).andExpect(jsonPath("$.completed").value(true));
  }

  @Test
  void finalWonOnPenalties() throws Exception {
    UUID cup = createCup(ana, PRIVATE, t.get(0), t.get(1));
    UUID fin = matchAt(cup, 1, 0);
    goals(fin, 1, 1);
    mvc.perform(get("/api/matches/{id}", fin).with(as(ana))).andExpect(jsonPath("$.knockout").value(true));
    finish(fin, "AWAY").andExpect(status().isOk());
    bracket(cup).andExpect(jsonPath("$.winner.id").value(t.get(1).toString()))
        .andExpect(jsonPath("$.matches[0].penaltyWinner").value("AWAY"));
  }

  @Test
  void unseededCupPlacesAllTeams() throws Exception {
    String body = mvc.perform(post("/api/competitions").with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"name\":\"Draw\",\"type\":\"CUP\",\"visibility\":\"PRIVATE\"}"))
        .andReturn().getResponse().getContentAsString();
    UUID cup = UUID.fromString(json.readTree(body).get("id").asString());
    for (UUID team : t.subList(0, 5)) {
      mvc.perform(post("/api/competitions/{id}/teams", cup).with(as(ana)).contentType(APPLICATION_JSON)
          .content(toJson(Map.of("teamId", team)))).andExpect(status().isOk());
    }
    mvc.perform(post("/api/competitions/{id}/generate", cup).with(as(ana))).andExpect(status().isOk());
    assertThat(competitionMatches(cup)).hasSize(4);
    List<UUID> placed = jdbc.sql("""
            SELECT BIN_TO_UUID(x.team) FROM (
              SELECT m.home_team_id team FROM sport_match m JOIN stage s ON s.id = m.stage_id WHERE s.competition_id = UUID_TO_BIN(:c) AND m.round <= 2
              UNION ALL
              SELECT m.away_team_id FROM sport_match m JOIN stage s ON s.id = m.stage_id WHERE s.competition_id = UUID_TO_BIN(:c) AND m.round <= 2
            ) x WHERE x.team IS NOT NULL
            """)
        .param("c", cup.toString()).query(UUID.class).list();
    assertThat(placed).containsExactlyInAnyOrderElementsOf(t.subList(0, 5));
  }

  @Test
  void tooManyTeams() throws Exception {
    String body = mvc.perform(post("/api/competitions").with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"name\":\"Huge\",\"type\":\"CUP\",\"visibility\":\"PRIVATE\"}"))
        .andReturn().getResponse().getContentAsString();
    UUID cup = UUID.fromString(json.readTree(body).get("id").asString());
    for (int i = 0; i < 65; i++) {
      UUID team = UUID.randomUUID();
      jdbc.sql("INSERT INTO team (id, created_at, name, sport) VALUES (UUID_TO_BIN(:t), NOW(6), :n, 'FOOTBALL')")
          .param("t", team.toString()).param("n", "X" + i).update();
      jdbc.sql("INSERT INTO competition_team (id, created_at, competition_id, team_id) VALUES (UUID_TO_BIN(UUID()), NOW(6), UUID_TO_BIN(:c), UUID_TO_BIN(:t))")
          .param("c", cup.toString()).param("t", team.toString()).update();
    }
    mvc.perform(post("/api/competitions/{id}/generate", cup).with(as(ana)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOO_MANY_TEAMS"));
  }

  @Test
  void bracketOnLeague() throws Exception {
    UUID league = createLeague(ana, PRIVATE, t.get(0), t.get(1));
    bracket(league).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_A_CUP"));
  }

  @Test
  void cupTeamsLockAfterResult() throws Exception {
    UUID cup = createCup(ana, PRIVATE, t.get(0), t.get(1), t.get(2));
    UUID semi = matchAt(cup, 1, 1); // seeds 2 v 3
    goals(semi, 1, 0);
    finish(semi, null).andExpect(status().isOk());
    mvc.perform(post("/api/competitions/{id}/teams", cup).with(as(ana)).contentType(APPLICATION_JSON).content(toJson(Map.of("teamId", t.get(3)))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAMS_LOCKED"));
  }

  @Test
  void oneOffFinishIgnoresPenaltyWinner() throws Exception {
    UUID m = createPickup(ana, PRIVATE);
    finish(m, "HOME").andExpect(status().isOk()).andExpect(jsonPath("$.penaltyWinner").doesNotExist())
        .andExpect(jsonPath("$.knockout").value(false));
    lifecycle(m, "reopen").andExpect(status().isOk());
    lifecycle(m, "cancel").andExpect(status().isOk());
  }
}
