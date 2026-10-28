package com.sportsapp.match;

import static com.sportsapp.common.Visibility.PRIVATE;
import static com.sportsapp.common.Visibility.PUBLIC;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class MatchApiTest extends IntegrationTest {

  private ResultActions create(TestUser u, Map<String, Object> body) throws Exception {
    Map<String, Object> b = new HashMap<>(body);
    b.putIfAbsent("visibility", "PRIVATE");
    return mvc.perform(post("/api/matches").with(as(u)).contentType(APPLICATION_JSON).content(toJson(b)));
  }

  @Test
  void createPickup() throws Exception {
    TestUser ana = register("Ana");
    create(ana, Map.of("homeLabel", " Bibs ", "awayLabel", "Non-bibs", "venue", "Park", "kickoffAt", "2026-11-01T18:00:00Z"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.home.name").value("Bibs"))
        .andExpect(jsonPath("$.home.teamId").doesNotExist())
        .andExpect(jsonPath("$.away.name").value("Non-bibs"))
        .andExpect(jsonPath("$.score.home").value(0))
        .andExpect(jsonPath("$.score.away").value(0))
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.sport").value("FOOTBALL"))
        .andExpect(jsonPath("$.visibility").value("PRIVATE"))
        .andExpect(jsonPath("$.kickoffAt").value("2026-11-01T18:00:00Z"))
        .andExpect(jsonPath("$.venue").value("Park"))
        .andExpect(jsonPath("$.lineup").value(empty()))
        .andExpect(jsonPath("$.events").value(empty()))
        .andExpect(jsonPath("$.canAdmin").value(true))
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void createTeamMatch() throws Exception {
    TestUser ana = register("Ana");
    UUID a = createTeam(ana, "Alpha");
    UUID b = createTeam(ana, "Beta");
    create(ana, Map.of("homeTeamId", a, "awayTeamId", b))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.home.teamId").value(a.toString()))
        .andExpect(jsonPath("$.home.name").value("Alpha"))
        .andExpect(jsonPath("$.away.name").value("Beta"));
  }

  @Test
  void createTeamMatchNeedsAdminOfBothTeams() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID a = createTeam(ana, "Alpha");
    UUID b = createTeam(ivan, "Beta");
    create(ana, Map.of("homeTeamId", a, "awayTeamId", b)).andExpect(status().isNotFound());
    putOnRoster(b, ana.playerId());
    create(ana, Map.of("homeTeamId", a, "awayTeamId", b)).andExpect(status().isForbidden());
  }

  @Test
  void sameTeamBothSides() throws Exception {
    TestUser ana = register("Ana");
    UUID a = createTeam(ana, "Alpha");
    create(ana, Map.of("homeTeamId", a, "awayTeamId", a))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SAME_TEAM"));
  }

  @Test
  void sidesMustBeTwoTeamsOrTwoLabels() throws Exception {
    TestUser ana = register("Ana");
    UUID a = createTeam(ana, "Alpha");
    create(ana, Map.of("homeTeamId", a, "awayLabel", "Bibs"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SIDES"));
    create(ana, Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SIDES"));
    create(ana, Map.of("homeLabel", "Bibs", "awayLabel", "  "))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SIDES"));
  }

  @Test
  void createNeedsVisibilityAndAuth() throws Exception {
    TestUser ana = register("Ana");
    mvc.perform(post("/api/matches").with(as(ana)).contentType(APPLICATION_JSON).content("{\"homeLabel\":\"A\",\"awayLabel\":\"B\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.visibility").value("NotNull"));
    mvc.perform(post("/api/matches").contentType(APPLICATION_JSON).content("{\"homeLabel\":\"A\",\"awayLabel\":\"B\",\"visibility\":\"PUBLIC\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void privateMatchVisibility() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    TestUser zoe = register("Zoe");
    UUID team = createTeam(ana, "Alpha");
    putOnRoster(team, ivan.playerId());
    UUID m = createPickup(ana, PRIVATE);
    mvc.perform(get("/api/matches/{id}", m).with(as(zoe))).andExpect(status().isNotFound());
    mvc.perform(get("/api/matches/{id}", m)).andExpect(status().isNotFound());
    mvc.perform(get("/api/matches/{id}", m).with(as(ivan))).andExpect(status().isNotFound());
    setLineup(ana, m, ivan.playerId(), Side.HOME);
    mvc.perform(get("/api/matches/{id}", m).with(as(ivan)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canAdmin").value(false))
        .andExpect(jsonPath("$.lineup[0].name").value("Ivan"));
    mvc.perform(get("/api/matches/{id}", UUID.randomUUID()).with(as(ana))).andExpect(status().isNotFound());
  }

  @Test
  void publicMatchReadableAnonymously() throws Exception {
    TestUser ana = register("Ana");
    UUID m = createPickup(ana, PUBLIC);
    mvc.perform(get("/api/matches/{id}", m)).andExpect(status().isOk()).andExpect(jsonPath("$.canAdmin").value(false));
  }

  @Test
  void patchDetailsAndStaleVersion() throws Exception {
    TestUser ana = register("Ana");
    UUID m = createPickup(ana, PRIVATE);
    mvc.perform(patch("/api/matches/{id}", m).with(as(ana)).contentType(APPLICATION_JSON).content("{\"version\":0,\"venue\":\"Park\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.venue").value("Park"))
        .andExpect(jsonPath("$.visibility").value("PRIVATE"))
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(patch("/api/matches/{id}", m).with(as(ana)).contentType(APPLICATION_JSON).content("{\"version\":0,\"venue\":\"Other\"}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE"));
    mvc.perform(patch("/api/matches/{id}", m).with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"version\":1,\"venue\":\"\",\"visibility\":\"PUBLIC\",\"kickoffAt\":\"2026-11-02T10:00:00Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.venue").doesNotExist())
        .andExpect(jsonPath("$.visibility").value("PUBLIC"))
        .andExpect(jsonPath("$.kickoffAt").value("2026-11-02T10:00:00Z"));
  }

  @Test
  void patchNeedsAdmin() throws Exception {
    TestUser ana = register("Ana");
    TestUser zoe = register("Zoe");
    UUID m = createPickup(ana, PUBLIC);
    mvc.perform(patch("/api/matches/{id}", m).with(as(zoe)).contentType(APPLICATION_JSON).content("{\"version\":0,\"venue\":\"X\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void mineListsGrantedAndLineupMatches() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID team = createTeam(ana, "Alpha");
    putOnRoster(team, ivan.playerId());
    UUID m = createPickup(ana, PRIVATE);
    mvc.perform(get("/api/matches/mine").with(as(ana)))
        .andExpect(jsonPath("$[*].id").value(contains(m.toString())))
        .andExpect(jsonPath("$[0].homeName").value("Bibs"))
        .andExpect(jsonPath("$[0].score.home").value(0));
    mvc.perform(get("/api/matches/mine").with(as(ivan))).andExpect(jsonPath("$").value(empty()));
    setLineup(ana, m, ivan.playerId(), Side.AWAY);
    mvc.perform(get("/api/matches/mine").with(as(ivan))).andExpect(jsonPath("$[*].id").value(contains(m.toString())));
  }

  @Test
  void mineOrdersUnscheduledFirstThenNewestKickoff() throws Exception {
    TestUser ana = register("Ana");
    String old = create(ana, Map.of("homeLabel", "A", "awayLabel", "B", "kickoffAt", "2026-01-01T10:00:00Z")).andReturn().getResponse().getContentAsString();
    String recent = create(ana, Map.of("homeLabel", "A", "awayLabel", "B", "kickoffAt", "2026-06-01T10:00:00Z")).andReturn().getResponse().getContentAsString();
    String none = create(ana, Map.of("homeLabel", "A", "awayLabel", "B")).andReturn().getResponse().getContentAsString();
    mvc.perform(get("/api/matches/mine").with(as(ana)))
        .andExpect(jsonPath("$[*].id").value(contains(id(none), id(recent), id(old))));
  }

  private String id(String body) {
    return json.readTree(body).get("id").asString();
  }
}
