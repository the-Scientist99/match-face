package com.sportsapp.competition;

import static com.sportsapp.common.Visibility.PRIVATE;
import static com.sportsapp.common.Visibility.PUBLIC;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import com.sportsapp.match.Side;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompetitionAccessTest extends IntegrationTest {

  private TestUser ana;
  private TestUser ivan;
  private TestUser zoe;
  private UUID alpha;
  private UUID beta;

  @BeforeEach
  void setUp() throws Exception {
    ana = register("Ana");
    ivan = register("Ivan");
    zoe = register("Zoe");
    alpha = createTeam(ana, "Alpha");
    beta = createTeam(ana, "Beta");
  }

  @Test
  void privateLeagueVisibleToRosterMembersOnly() throws Exception {
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    putOnRoster(alpha, ivan.playerId());
    mvc.perform(get("/api/competitions/{id}", c).with(as(ivan))).andExpect(status().isOk()).andExpect(jsonPath("$.canAdmin").value(false));
    mvc.perform(get("/api/competitions/{id}", c).with(as(zoe))).andExpect(status().isNotFound());
    mvc.perform(get("/api/competitions/{id}", c)).andExpect(status().isNotFound());
    mvc.perform(get("/api/matches/{id}", competitionMatches(c).getFirst()).with(as(ivan))).andExpect(status().isOk());
    mvc.perform(get("/api/matches/{id}", competitionMatches(c).getFirst()).with(as(zoe))).andExpect(status().isNotFound());
  }

  @Test
  void publicLeagueAnonymous() throws Exception {
    UUID c = createLeague(ana, PUBLIC, alpha, beta);
    mvc.perform(get("/api/competitions/{id}", c)).andExpect(status().isOk()).andExpect(jsonPath("$.canAdmin").value(false));
    mvc.perform(get("/api/matches/{id}", competitionMatches(c).getFirst())).andExpect(status().isOk());
  }

  @Test
  void competitionAdminAdminsItsMatches() throws Exception {
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    putOnRoster(alpha, ivan.playerId());
    UUID m = competitionMatches(c).getFirst();
    addEvent(ana, m, "type", "GOAL", "side", "HOME");
    mvc.perform(post("/api/matches/{id}/events", m).with(as(ivan)).contentType(APPLICATION_JSON).content("{\"type\":\"GOAL\",\"side\":\"HOME\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/matches/{id}", m).with(as(ana))).andExpect(jsonPath("$.canAdmin").value(true));
  }

  @Test
  void teamReadableThroughPublicLeague() throws Exception {
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    mvc.perform(get("/api/teams/{id}", alpha).with(as(zoe))).andExpect(status().isNotFound());
    mvc.perform(get("/api/competitions/{id}", c).with(as(ana))); // version lookup below
    long v = json.readTree(mvc.perform(get("/api/competitions/{id}", c).with(as(ana))).andReturn().getResponse().getContentAsString()).get("version").asLong();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/competitions/{id}", c).with(as(ana))
            .contentType(APPLICATION_JSON).content("{\"version\":" + v + ",\"visibility\":\"PUBLIC\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/teams/{id}", alpha).with(as(zoe))).andExpect(status().isOk()).andExpect(jsonPath("$.canAdmin").value(false));
    mvc.perform(get("/api/teams/{id}", alpha)).andExpect(status().isOk());
  }

  @Test
  void privateLeagueMatchHiddenInStrangerProfileStats() throws Exception {
    UUID marko = createGuest(ana, "Marko");
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    UUID m = competitionMatches(c).getFirst();
    String side = jdbc.sql("SELECT IF(home_team_id = UUID_TO_BIN(:a), 'HOME', 'AWAY') FROM sport_match WHERE id = UUID_TO_BIN(:m)")
        .param("a", alpha.toString()).param("m", m.toString()).query(String.class).single();
    setLineup(ana, m, marko, Side.valueOf(side));
    addEvent(ana, m, "type", "GOAL", "side", side, "playerId", marko);
    finish(ana, m);
    mvc.perform(get("/api/players/{id}", marko).with(as(ana))).andExpect(jsonPath("$.totals.goals").value(1));
    mvc.perform(get("/api/players/{id}", marko).with(as(zoe))).andExpect(jsonPath("$.totals.goals").value(0));
  }

  @Test
  void mineIncludesLeagueMatchesForRosterMembers() throws Exception {
    UUID c = createLeague(ana, PRIVATE, alpha, beta);
    UUID m = competitionMatches(c).getFirst();
    mvc.perform(get("/api/matches/mine").with(as(ivan))).andExpect(jsonPath("$[*].id").value(not(hasItem(m.toString()))));
    putOnRoster(alpha, ivan.playerId());
    mvc.perform(get("/api/matches/mine").with(as(ivan)))
        .andExpect(jsonPath("$[*].id").value(hasItem(m.toString())))
        .andExpect(jsonPath("$[0].competitionName").value("League"));
    mvc.perform(get("/api/matches/mine").with(as(ana))).andExpect(jsonPath("$[*].id").value(hasItem(m.toString())));
  }

  @Test
  void oneOffMatchRulesUnchanged() throws Exception {
    UUID pickup = createPickup(ana, PRIVATE);
    createLeague(ana, PUBLIC, alpha, beta);
    mvc.perform(get("/api/matches/{id}", pickup).with(as(zoe))).andExpect(status().isNotFound());
  }
}
