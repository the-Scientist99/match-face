package com.sportsapp.match;

import static com.sportsapp.common.Visibility.PRIVATE;
import static com.sportsapp.common.Visibility.PUBLIC;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    mvc.perform(get("/api/matches/open").with(as(bo))).andExpect(jsonPath("$.items[0].spotsLeft").value(2));
    join(ivan, m, Side.HOME).andExpect(status().isOk());
    join(zoe, m, Side.HOME).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SIDE_FULL"));
    mvc.perform(get("/api/matches/open").with(as(bo))).andExpect(jsonPath("$.items[0].spotsLeft").value(1));
    join(zoe, m, Side.AWAY).andExpect(status().isOk());
    mvc.perform(get("/api/matches/open").with(as(bo))).andExpect(jsonPath("$.items").value(empty()));
    // 0 removes the limit; the match is open again
    mvc.perform(patch("/api/matches/{id}", m).with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("version", version(m), "maxPerSide", 0))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.maxPerSide").doesNotExist());
    mvc.perform(get("/api/matches/open").with(as(bo)))
        .andExpect(jsonPath("$.items[*].id").value(contains(m.toString())))
        .andExpect(jsonPath("$.items[0].spotsLeft").doesNotExist());
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
    mvc.perform(get("/api/matches/open").with(as(ivan))).andExpect(jsonPath("$.items[*].id").value(contains(upcoming.toString())));
    mvc.perform(get("/api/matches/open").with(as(ana))).andExpect(jsonPath("$.items").value(empty()));
    join(ivan, upcoming, Side.HOME).andExpect(status().isOk());
    mvc.perform(get("/api/matches/open").with(as(ivan))).andExpect(jsonPath("$.items").value(empty()));
  }

  /** A public pickup; {@code venue} may be null. */
  private UUID pickup(TestUser owner, String home, String away, String venue, Instant kickoff) throws Exception {
    Map<String, Object> body = new HashMap<>(Map.of("homeLabel", home, "awayLabel", away, "visibility", "PUBLIC", "kickoffAt", kickoff.toString()));
    if (venue != null) body.put("venue", venue);
    String response = mvc.perform(post("/api/matches").with(as(owner)).contentType(APPLICATION_JSON).content(toJson(body)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return UUID.fromString(json.readTree(response).get("id").asString());
  }

  private ResultActions search(TestUser user, String q) throws Exception {
    return mvc.perform(get("/api/matches/open").param("q", q).with(as(user)));
  }

  private ResultActions openPage(TestUser user, int page) throws Exception {
    return mvc.perform(get("/api/matches/open").param("page", String.valueOf(page)).with(as(user)));
  }

  @Test
  void searchFindsSideNamesAndVenueIgnoringCaseAndAccents() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    Instant kickoff = Instant.now().plusSeconds(3600);
    UUID byHome = pickup(ana, "Crveni", "Plavi", null, kickoff);
    UUID byAway = pickup(ana, "Bijeli", "Žuti", null, kickoff);
    UUID byVenue = pickup(ana, "Bibs", "Non-bibs", "Tuško Sports Hall", kickoff);
    search(ivan, "crven").andExpect(jsonPath("$.items[*].id").value(contains(byHome.toString())));
    search(ivan, "ZUTI").andExpect(jsonPath("$.items[*].id").value(contains(byAway.toString())));
    search(ivan, " tusko ").andExpect(jsonPath("$.items[*].id").value(contains(byVenue.toString())));
    search(ivan, "nowhere").andExpect(jsonPath("$.items").value(empty())).andExpect(jsonPath("$.totalPages").value(0));
  }

  @Test
  void searchTakesWildcardsLiterally() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    Instant kickoff = Instant.now().plusSeconds(3600);
    UUID percent = pickup(ana, "100%", "Rest", null, kickoff);
    pickup(ana, "Plain", "Other", null, kickoff);
    search(ivan, "%").andExpect(jsonPath("$.items[*].id").value(contains(percent.toString())));
    search(ivan, "_").andExpect(jsonPath("$.items").value(empty()));
  }

  @Test
  void openPagesSoonestFirst() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 10; i++) ids.add(pickup(ana, "H" + i, "A" + i, null, Instant.now().plusSeconds(3600 + 60L * i)));
    openPage(ivan, 0).andExpect(jsonPath("$.items", hasSize(9))).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.items[0].id").value(ids.getFirst().toString()));
    openPage(ivan, 1).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.items[*].id").value(contains(ids.getLast().toString())));
  }

  @Test
  void matchesWithTheSameKickoffStayInOnePlaceAcrossPages() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    Instant kickoff = Instant.now().plusSeconds(3600);
    Set<UUID> created = new HashSet<>();
    for (int i = 0; i < 10; i++) created.add(pickup(ana, "H" + i, "A" + i, null, kickoff));
    jdbc.sql("UPDATE sport_match SET created_at = '2026-01-01 00:00:00'").update(); // only the id can break the tie
    Set<String> seen = new HashSet<>();
    for (int page = 0; page < 2; page++) {
      String body = openPage(ivan, page).andReturn().getResponse().getContentAsString();
      json.readTree(body).get("items").forEach(m -> seen.add(m.get("id").asString()));
    }
    assertEquals(created.stream().map(UUID::toString).collect(Collectors.toSet()), seen);
  }

  @Test
  void aPagePastTheEndGivesTheLastOne() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    for (int i = 0; i < 10; i++) pickup(ana, "H" + i, "A" + i, null, Instant.now().plusSeconds(3600));
    openPage(ivan, Integer.MAX_VALUE).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.items", hasSize(1)));
  }

  @Test
  void aNegativePageIsRefused() throws Exception {
    TestUser ivan = register("Ivan");
    openPage(ivan, -1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FIELD"));
  }
}
