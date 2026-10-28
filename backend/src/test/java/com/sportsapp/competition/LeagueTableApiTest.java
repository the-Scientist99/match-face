package com.sportsapp.competition;

import static com.sportsapp.common.Visibility.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import com.sportsapp.match.Side;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** A 3-team league: Alpha (Marko, Ivan), Beta (Luka), Gamma. */
class LeagueTableApiTest extends IntegrationTest {

  private TestUser ana;
  private UUID alpha;
  private UUID beta;
  private UUID marko;
  private UUID ivan;
  private UUID luka;
  private UUID league;
  private UUID alphaBeta;
  private Side alphaSide;

  @BeforeEach
  void setUpLeague() throws Exception {
    ana = register("Ana");
    marko = createGuest(ana, "Marko");
    ivan = createGuest(ana, "Ivan");
    luka = createGuest(ana, "Luka");
    alpha = createTeam(ana, "Alpha", marko, ivan);
    beta = createTeam(ana, "Beta", luka);
    UUID gamma = createTeam(ana, "Gamma");
    league = createLeague(ana, PRIVATE, alpha, beta, gamma);
    alphaBeta = jdbc.sql("""
            SELECT BIN_TO_UUID(m.id) FROM sport_match m
            WHERE (m.home_team_id = UUID_TO_BIN(:a) AND m.away_team_id = UUID_TO_BIN(:b))
               OR (m.home_team_id = UUID_TO_BIN(:b) AND m.away_team_id = UUID_TO_BIN(:a))
            """)
        .param("a", alpha.toString()).param("b", beta.toString()).query(UUID.class).single();
    alphaSide = Side.valueOf(jdbc.sql("SELECT IF(home_team_id = UUID_TO_BIN(:a), 'HOME', 'AWAY') FROM sport_match WHERE id = UUID_TO_BIN(:m)")
        .param("a", alpha.toString()).param("m", alphaBeta.toString()).query(String.class).single());
    setLineup(ana, alphaBeta, marko, alphaSide, ivan, alphaSide, luka, alphaSide.opposite());
  }

  private ResultActions standings() throws Exception {
    return mvc.perform(get("/api/competitions/{id}/standings", league).with(as(ana)));
  }

  private ResultActions stats() throws Exception {
    return mvc.perform(get("/api/competitions/{id}/stats", league).with(as(ana)));
  }

  @Test
  void tableBeforeAnyResult() throws Exception {
    standings().andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[*].name").value(contains("Alpha", "Beta", "Gamma")))
        .andExpect(jsonPath("$[*].points").value(contains(0, 0, 0)))
        .andExpect(jsonPath("$[*].position").value(contains(1, 2, 3)));
  }

  @Test
  void tableAfterResults() throws Exception {
    addEvent(ana, alphaBeta, "type", "GOAL", "side", alphaSide, "playerId", marko);
    addEvent(ana, alphaBeta, "type", "OWN_GOAL", "side", alphaSide.opposite(), "playerId", luka); // counts for Alpha
    finish(ana, alphaBeta);
    standings()
        .andExpect(jsonPath("$[0].name").value("Alpha"))
        .andExpect(jsonPath("$[0].played").value(1))
        .andExpect(jsonPath("$[0].won").value(1))
        .andExpect(jsonPath("$[0].goalsFor").value(2))
        .andExpect(jsonPath("$[0].points").value(3))
        .andExpect(jsonPath("$[2].name").value("Beta"))
        .andExpect(jsonPath("$[2].lost").value(1))
        .andExpect(jsonPath("$[2].goalsAgainst").value(2))
        .andExpect(jsonPath("$[2].position").value(3));
  }

  @Test
  void unfinishedMatchesIgnored() throws Exception {
    addEvent(ana, alphaBeta, "type", "GOAL", "side", alphaSide, "playerId", marko);
    standings().andExpect(jsonPath("$[*].points").value(contains(0, 0, 0)));
    stats().andExpect(jsonPath("$.topScorers").value(empty()));
  }

  @Test
  void unknownScorerCountsForTeamNotPlayer() throws Exception {
    addEvent(ana, alphaBeta, "type", "GOAL", "side", alphaSide);
    finish(ana, alphaBeta);
    standings().andExpect(jsonPath("$[0].name").value("Alpha")).andExpect(jsonPath("$[0].goalsFor").value(1));
    stats().andExpect(jsonPath("$.topScorers").value(empty()));
  }

  @Test
  void statsLeaders() throws Exception {
    addEvent(ana, alphaBeta, "type", "GOAL", "side", alphaSide, "playerId", marko, "assistPlayerId", ivan);
    addEvent(ana, alphaBeta, "type", "GOAL", "side", alphaSide, "playerId", marko);
    addEvent(ana, alphaBeta, "type", "GOAL", "side", alphaSide, "playerId", ivan);
    addEvent(ana, alphaBeta, "type", "YELLOW", "side", alphaSide, "playerId", ivan);
    addEvent(ana, alphaBeta, "type", "RED", "side", alphaSide.opposite(), "playerId", luka);
    finish(ana, alphaBeta);
    stats().andExpect(status().isOk())
        .andExpect(jsonPath("$.topScorers[*].name").value(contains("Marko", "Ivan")))
        .andExpect(jsonPath("$.topScorers[*].value").value(contains(2, 1)))
        .andExpect(jsonPath("$.topAssists[*].name").value(contains("Ivan")))
        .andExpect(jsonPath("$.topAssists[0].value").value(1))
        .andExpect(jsonPath("$.cards[*].name").value(contains("Luka", "Ivan")))
        .andExpect(jsonPath("$.cards[0].red").value(1))
        .andExpect(jsonPath("$.cards[0].yellow").value(0))
        .andExpect(jsonPath("$.cards[1].yellow").value(1));
  }

  @Test
  void matchesOrderedByRound() throws Exception {
    String body = mvc.perform(get("/api/competitions/{id}/matches", league).with(as(ana)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
        .andReturn().getResponse().getContentAsString();
    List<Integer> rounds = json.readTree(body).valueStream().map(n -> n.get("round").asInt()).toList();
    assertThat(rounds).isSorted().containsExactly(1, 2, 3);
  }

  @Test
  void standingsOnCup() throws Exception {
    String body = mvc.perform(post("/api/competitions").with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"name\":\"Cup\",\"type\":\"CUP\",\"visibility\":\"PRIVATE\"}"))
        .andReturn().getResponse().getContentAsString();
    String cup = json.readTree(body).get("id").asString();
    mvc.perform(get("/api/competitions/{id}/standings", cup).with(as(ana)))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_A_LEAGUE"));
  }

  @Test
  void privateTableHiddenFromStranger() throws Exception {
    TestUser zoe = register("Zoe");
    mvc.perform(get("/api/competitions/{id}/standings", league).with(as(zoe))).andExpect(status().isNotFound());
    mvc.perform(get("/api/competitions/{id}/stats", league)).andExpect(status().isNotFound());
    mvc.perform(get("/api/competitions/{id}/matches", league).with(as(zoe))).andExpect(status().isNotFound());
  }
}
