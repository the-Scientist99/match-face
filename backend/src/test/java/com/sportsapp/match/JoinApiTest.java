package com.sportsapp.match;

import static com.sportsapp.common.Visibility.PRIVATE;
import static com.sportsapp.common.Visibility.PUBLIC;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class JoinApiTest extends IntegrationTest {

  private ResultActions join(TestUser u, UUID matchId, Side side) throws Exception {
    return mvc.perform(post("/api/matches/{id}/join", matchId).with(as(u)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("side", side))));
  }

  @Test
  void joinAndLeaveAPublicPickup() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID m = createUpcomingPickup(ana, PUBLIC);
    mvc.perform(get("/api/matches/{id}", m).with(as(ivan)))
        .andExpect(jsonPath("$.joinable").value(true)).andExpect(jsonPath("$.mySide").doesNotExist());
    join(ivan, m, Side.AWAY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mySide").value("AWAY"))
        .andExpect(jsonPath("$.lineup[0].name").value("Ivan"))
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(get("/api/matches/mine").with(as(ivan))).andExpect(jsonPath("$[*].id").value(contains(m.toString())));
    join(ivan, m, Side.HOME).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
    mvc.perform(delete("/api/matches/{id}/join", m).with(as(ivan)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lineup").value(empty()))
        .andExpect(jsonPath("$.mySide").doesNotExist());
    mvc.perform(delete("/api/matches/{id}/join", m).with(as(ivan))).andExpect(status().isNotFound());
  }

  @Test
  void onlyPublicPickupsAreJoinable() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    join(ivan, createUpcomingPickup(ana, PRIVATE), Side.HOME).andExpect(status().isNotFound());
    String body = mvc.perform(post("/api/matches").with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("homeTeamId", createTeam(ana, "Alpha"), "awayTeamId", createTeam(ana, "Beta"),
                "visibility", "PUBLIC", "kickoffAt", Instant.now().plusSeconds(3600).toString()))))
        .andReturn().getResponse().getContentAsString();
    UUID teamMatch = UUID.fromString(json.readTree(body).get("id").asString());
    join(ivan, teamMatch, Side.HOME).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_JOINABLE"));
    mvc.perform(post("/api/matches/{id}/join", teamMatch).contentType(APPLICATION_JSON).content("{\"side\":\"HOME\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void cannotLeaveWithEvents() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID m = createPickup(ana, PUBLIC);
    join(ivan, m, Side.HOME).andExpect(status().isOk());
    addEvent(ana, m, "type", "GOAL", "side", "HOME", "playerId", ivan.playerId());
    mvc.perform(delete("/api/matches/{id}/join", m).with(as(ivan)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAYER_HAS_EVENTS"));
  }

  private UUID createCappedPickup(TestUser owner, int maxPerSide) throws Exception {
    String body = mvc.perform(post("/api/matches").with(as(owner)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("homeLabel", "Bibs", "awayLabel", "Non-bibs", "visibility", "PUBLIC", "maxPerSide", maxPerSide,
                "kickoffAt", Instant.now().plusSeconds(3600).toString()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.maxPerSide").value(maxPerSide))
        .andReturn().getResponse().getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asString());
  }

  @Test
  void aFullSideRefusesJoinsAndFullMatchesLeaveTheOpenList() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    TestUser zoe = register("Zoe");
    TestUser bo = register("Bo");
    UUID m = createCappedPickup(ana, 1);
    mvc.perform(get("/api/matches/open").with(as(bo))).andExpect(jsonPath("$[0].spotsLeft").value(2));
    join(ivan, m, Side.HOME).andExpect(status().isOk());
    join(zoe, m, Side.HOME).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SIDE_FULL"));
    mvc.perform(get("/api/matches/open").with(as(bo))).andExpect(jsonPath("$[0].spotsLeft").value(1));
    join(zoe, m, Side.AWAY).andExpect(status().isOk());
    mvc.perform(get("/api/matches/open").with(as(bo))).andExpect(jsonPath("$").value(empty()));
    // 0 removes the limit; the match is open again
    mvc.perform(patch("/api/matches/{id}", m).with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("version", version(m), "maxPerSide", 0))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.maxPerSide").doesNotExist());
    mvc.perform(get("/api/matches/open").with(as(bo)))
        .andExpect(jsonPath("$[*].id").value(contains(m.toString())))
        .andExpect(jsonPath("$[0].spotsLeft").doesNotExist());
  }

  @Test
  void onlyPickupsHaveACap() throws Exception {
    TestUser ana = register("Ana");
    mvc.perform(post("/api/matches").with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("homeTeamId", createTeam(ana, "Alpha"), "awayTeamId", createTeam(ana, "Beta"), "visibility", "PUBLIC",
                "maxPerSide", 5, "kickoffAt", Instant.now().plusSeconds(3600).toString()))))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FIELD"));
  }

  @Test
  void openListsUpcomingPublicPickupsIAmNotIn() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID upcoming = createUpcomingPickup(ana, PUBLIC);
    createUpcomingPickup(ana, PRIVATE);
    createPickup(ana, PUBLIC); // already started
    mvc.perform(get("/api/matches/open").with(as(ivan))).andExpect(jsonPath("$[*].id").value(contains(upcoming.toString())));
    mvc.perform(get("/api/matches/open").with(as(ana))).andExpect(jsonPath("$").value(empty()));
    join(ivan, upcoming, Side.HOME).andExpect(status().isOk());
    mvc.perform(get("/api/matches/open").with(as(ivan))).andExpect(jsonPath("$").value(empty()));
  }
}
