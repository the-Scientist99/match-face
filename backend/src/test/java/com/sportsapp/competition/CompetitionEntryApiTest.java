package com.sportsapp.competition;

import static com.sportsapp.common.Visibility.PRIVATE;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class CompetitionEntryApiTest extends IntegrationTest {

  private TestUser ana;
  private TestUser bob;
  private UUID alpha;
  private UUID bees;
  private UUID bobGuest;

  @BeforeEach
  void setUp() throws Exception {
    ana = register("Ana");
    bob = register("Bob");
    alpha = createTeam(ana, "Alpha");
    bobGuest = createGuest(bob, "Goran");
    bees = createTeam(bob, "Bees", bobGuest);
  }

  private UUID create(String visibility, boolean entriesOpen) throws Exception {
    String body = mvc.perform(post("/api/competitions").with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("name", "Open", "type", "LEAGUE", "visibility", visibility, "entriesOpen", entriesOpen))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asString());
  }

  private ResultActions enter(TestUser user, UUID competition, UUID team) throws Exception {
    return mvc.perform(post("/api/competitions/{id}/entries", competition).with(as(user)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("teamId", team))));
  }

  private ResultActions approve(TestUser user, UUID competition, UUID team, String body) throws Exception {
    return mvc.perform(post("/api/competitions/{id}/entries/{teamId}/approve", competition, team).with(as(user))
        .contentType(APPLICATION_JSON).content(body));
  }

  /** Ana's Alpha against Bob's Bees, entered and approved, fixtures generated; returns the competition. */
  private UUID playingLeague() throws Exception {
    UUID c = create("PUBLIC", true);
    mvc.perform(post("/api/competitions/{id}/teams", c).with(as(ana)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("teamId", alpha)))).andExpect(status().isOk());
    enter(bob, c, bees).andExpect(status().isOk());
    approve(ana, c, bees, "{}").andExpect(status().isOk());
    mvc.perform(post("/api/competitions/{id}/generate", c).with(as(ana))).andExpect(status().isOk());
    return c;
  }

  private String sideOf(UUID team, UUID match) {
    return jdbc.sql("SELECT IF(home_team_id = UUID_TO_BIN(:t), 'HOME', 'AWAY') FROM sport_match WHERE id = UUID_TO_BIN(:m)")
        .param("t", team.toString()).param("m", match.toString()).query(String.class).single();
  }

  private ResultActions lineup(TestUser user, UUID match, UUID player, String side) throws Exception {
    return mvc.perform(put("/api/matches/{id}/lineup", match).with(as(user)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("entries", List.of(Map.of("playerId", player, "side", side))))));
  }

  @Test
  void entriesNeedAPublicCompetition() throws Exception {
    mvc.perform(post("/api/competitions").with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("name", "Closed", "type", "LEAGUE", "visibility", "PRIVATE", "entriesOpen", true))))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ENTRIES_NEED_PUBLIC"));
    UUID c = create("PRIVATE", false);
    mvc.perform(patch("/api/competitions/{id}", c).with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"version\":0,\"entriesOpen\":true}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ENTRIES_NEED_PUBLIC"));
  }

  @Test
  void organizerApprovesACaptainsTeam() throws Exception {
    UUID c = create("PUBLIC", true);
    enter(bob, c, bees).andExpect(status().isOk()).andExpect(jsonPath("$.entries[*].name").value(contains("Bees")));
    mvc.perform(get("/api/competitions/{id}", c).with(as(ana)))
        .andExpect(jsonPath("$.entriesOpen").value(true))
        .andExpect(jsonPath("$.entries[*].teamId").value(contains(bees.toString())));
    approve(ana, c, bees, "{}").andExpect(status().isOk())
        .andExpect(jsonPath("$.teams[*].name").value(contains("Bees")))
        .andExpect(jsonPath("$.entries").value(empty()));
  }

  @Test
  void onlyTheOrganizerAndTheTeamsAdminSeeARequest() throws Exception {
    UUID c = create("PUBLIC", true);
    enter(bob, c, bees).andExpect(status().isOk());
    TestUser zoe = register("Zoe");
    mvc.perform(get("/api/competitions/{id}", c).with(as(zoe))).andExpect(jsonPath("$.entries").value(empty()));
    mvc.perform(get("/api/competitions/{id}", c)).andExpect(jsonPath("$.entries").value(empty()));
  }

  @Test
  void enteringNeedsOpenEntries() throws Exception {
    UUID c = create("PUBLIC", false);
    enter(bob, c, bees).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENTRIES_CLOSED"));
  }

  @Test
  void enteringAPrivateCompetitionIsNotFound() throws Exception {
    UUID c = create("PRIVATE", false);
    enter(bob, c, bees).andExpect(status().isNotFound());
  }

  @Test
  void onlyAdminsOfATeamCanEnterIt() throws Exception {
    UUID c = create("PUBLIC", true);
    enter(ana, c, bees).andExpect(status().isNotFound()); // she can't see the team
    putOnRoster(bees, ana.playerId());
    enter(ana, c, bees).andExpect(status().isForbidden());
  }

  @Test
  void aTeamEntersOnce() throws Exception {
    UUID c = create("PUBLIC", true);
    enter(bob, c, bees).andExpect(status().isOk());
    enter(bob, c, bees).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENTRY_EXISTS"));
    approve(ana, c, bees, "{}").andExpect(status().isOk());
    enter(bob, c, bees).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAM_ALREADY_ADDED"));
  }

  @Test
  void onlyTheOrganizerApproves() throws Exception {
    UUID c = create("PUBLIC", true);
    enter(bob, c, bees).andExpect(status().isOk());
    approve(bob, c, bees, "{}").andExpect(status().isForbidden());
    approve(ana, c, alpha, "{}").andExpect(status().isNotFound());
  }

  @Test
  void approvingAssignsASeed() throws Exception {
    UUID c = create("PUBLIC", true);
    mvc.perform(post("/api/competitions/{id}/teams", c).with(as(ana)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("teamId", alpha, "seed", 1)))).andExpect(status().isOk());
    enter(bob, c, bees).andExpect(status().isOk());
    approve(ana, c, bees, "{\"seed\":1}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SEED"));
    approve(ana, c, bees, "{\"seed\":2}").andExpect(status().isOk()).andExpect(jsonPath("$.teams[1].seed").value(2));
  }

  @Test
  void organizerRejectsAndCaptainWithdraws() throws Exception {
    UUID c = create("PUBLIC", true);
    TestUser zoe = register("Zoe");
    enter(bob, c, bees).andExpect(status().isOk());
    mvc.perform(delete("/api/competitions/{id}/entries/{teamId}", c, bees).with(as(zoe))).andExpect(status().isForbidden());
    mvc.perform(delete("/api/competitions/{id}/entries/{teamId}", c, bees).with(as(ana)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.entries").value(empty()));
    enter(bob, c, bees).andExpect(status().isOk());
    mvc.perform(delete("/api/competitions/{id}/entries/{teamId}", c, bees).with(as(bob)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.entries").value(empty()));
    mvc.perform(delete("/api/competitions/{id}/entries/{teamId}", c, bees).with(as(bob))).andExpect(status().isNotFound());
  }

  @Test
  void generatingClosesEntriesAndDropsPendingOnes() throws Exception {
    TestUser cara = register("Cara");
    UUID caraTeam = createTeam(cara, "Cats");
    UUID c = playingLeague();
    mvc.perform(get("/api/competitions/{id}", c).with(as(ana)))
        .andExpect(jsonPath("$.entriesOpen").value(false)).andExpect(jsonPath("$.entries").value(empty()));
    enter(cara, c, caraTeam).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENTRIES_CLOSED"));
    assertNoPending(c);
  }

  @Test
  void generatingDropsARequestStillWaiting() throws Exception {
    UUID c = create("PUBLIC", true);
    mvc.perform(post("/api/competitions/{id}/teams", c).with(as(ana)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("teamId", alpha)))).andExpect(status().isOk());
    UUID second = createTeam(ana, "Second");
    mvc.perform(post("/api/competitions/{id}/teams", c).with(as(ana)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("teamId", second)))).andExpect(status().isOk());
    enter(bob, c, bees).andExpect(status().isOk());
    mvc.perform(post("/api/competitions/{id}/generate", c).with(as(ana))).andExpect(status().isOk());
    assertNoPending(c);
  }

  private void assertNoPending(UUID competition) throws Exception {
    mvc.perform(get("/api/competitions/{id}", competition).with(as(ana))).andExpect(jsonPath("$.entries").value(empty()));
  }

  @Test
  void entriesOpenOnlyBeforeFixturesExist() throws Exception {
    UUID c = playingLeague();
    long v = jdbc.sql("SELECT version FROM competition WHERE id = UUID_TO_BIN(:c)").param("c", c.toString()).query(Long.class).single();
    mvc.perform(patch("/api/competitions/{id}", c).with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"version\":" + v + ",\"entriesOpen\":true}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPETITION_STARTED"));
  }

  @Test
  void goingPrivateClosesEntriesAndDropsPendingOnes() throws Exception {
    UUID c = create("PUBLIC", true);
    enter(bob, c, bees).andExpect(status().isOk());
    mvc.perform(patch("/api/competitions/{id}", c).with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"version\":0,\"visibility\":\"PRIVATE\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.entriesOpen").value(false)).andExpect(jsonPath("$.entries").value(empty()));
  }

  @Test
  void aCaptainPicksOnlyTheirOwnSide() throws Exception {
    UUID m = competitionMatches(playingLeague()).getFirst();
    String mine = sideOf(bees, m);
    String theirs = sideOf(alpha, m);
    mvc.perform(get("/api/matches/{id}", m).with(as(bob)))
        .andExpect(jsonPath("$.canAdmin").value(false)).andExpect(jsonPath("$.lineupSides").value(contains(mine)));
    mvc.perform(get("/api/matches/{id}", m).with(as(ana))).andExpect(jsonPath("$.lineupSides").value(containsInAnyOrder("HOME", "AWAY")));
    mvc.perform(get("/api/matches/{id}", m)).andExpect(jsonPath("$.lineupSides").value(empty()));
    lineup(bob, m, bobGuest, theirs).andExpect(status().isForbidden());
    lineup(bob, m, bobGuest, mine).andExpect(status().isOk()).andExpect(jsonPath("$.lineup[*].name").value(contains("Goran")));
  }

  @Test
  void aCaptainsSaveKeepsTheOtherSide() throws Exception {
    UUID m = competitionMatches(playingLeague()).getFirst();
    UUID anaGuest = createGuest(ana, "Marko");
    lineup(ana, m, anaGuest, sideOf(alpha, m)).andExpect(status().isOk());
    lineup(bob, m, bobGuest, sideOf(bees, m)).andExpect(status().isOk())
        .andExpect(jsonPath("$.lineup[*].name").value(containsInAnyOrder("Goran", "Marko")));
    mvc.perform(put("/api/matches/{id}/lineup", m).with(as(bob)).contentType(APPLICATION_JSON).content("{\"entries\":[]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.lineup[*].name").value(contains("Marko")));
  }

  @Test
  void aCaptainCantUseAPlayerOnTheOtherSide() throws Exception {
    UUID m = competitionMatches(playingLeague()).getFirst();
    lineup(ana, m, bobGuest, sideOf(alpha, m)).andExpect(status().isBadRequest());
    putOnRoster(alpha, bobGuest);
    lineup(ana, m, bobGuest, sideOf(alpha, m)).andExpect(status().isOk());
    lineup(bob, m, bobGuest, sideOf(bees, m)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_PLAYER"));
  }

  @Test
  void aCaptainsGuestJoinsTheirRoster() throws Exception {
    UUID m = competitionMatches(playingLeague()).getFirst();
    UUID newGuest = createGuest(bob, "Novi");
    lineup(bob, m, newGuest, sideOf(bees, m)).andExpect(status().isOk());
    mvc.perform(get("/api/teams/{id}", bees).with(as(bob))).andExpect(jsonPath("$.players[*].name").value(hasItem("Novi")));
  }

  @Test
  void anOrganizerCantPutTheirOwnPlayersOnSomeoneElsesRoster() throws Exception {
    UUID m = competitionMatches(playingLeague()).getFirst();
    UUID anaGuest = createGuest(ana, "Marko");
    lineup(ana, m, anaGuest, sideOf(bees, m)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAYER_NOT_AVAILABLE"));
    lineup(ana, m, bobGuest, sideOf(bees, m)).andExpect(status().isOk());
    mvc.perform(get("/api/teams/{id}", bees).with(as(bob))).andExpect(jsonPath("$.players", hasSize(1)));
  }

  @Test
  void anOrganizerKeepsALineupPlayerWhoLeftAnotherTeamsRosterOffThatRoster() throws Exception {
    UUID m = competitionMatches(playingLeague()).getFirst();
    lineup(ana, m, bobGuest, sideOf(bees, m)).andExpect(status().isOk());
    mvc.perform(put("/api/teams/{id}/players", bees).with(as(bob)).contentType(APPLICATION_JSON).content("{\"playerIds\":[]}"))
        .andExpect(status().isOk());
    lineup(ana, m, bobGuest, sideOf(bees, m)).andExpect(status().isOk());
    mvc.perform(get("/api/teams/{id}", bees).with(as(bob))).andExpect(jsonPath("$.players").value(empty()));
  }

  @Test
  void aCaptainDoesntRunTheMatch() throws Exception {
    UUID m = competitionMatches(playingLeague()).getFirst();
    mvc.perform(post("/api/matches/{id}/events", m).with(as(bob)).contentType(APPLICATION_JSON).content("{\"type\":\"GOAL\",\"side\":\"HOME\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/matches/{id}/finish", m).with(as(bob)).contentType(APPLICATION_JSON).content(toJson(Map.of("version", version(m)))))
        .andExpect(status().isForbidden());
  }

  @Test
  void aStrangerCantPickALineup() throws Exception {
    UUID m = competitionMatches(playingLeague()).getFirst();
    TestUser zoe = register("Zoe");
    lineup(zoe, m, zoe.playerId(), "HOME").andExpect(status().isForbidden());
  }

  @Test
  void aCaptainOffTheirOwnRosterStillFollowsTheCompetition() throws Exception {
    UUID c = playingLeague();
    mvc.perform(patch("/api/competitions/{id}", c).with(as(ana)).contentType(APPLICATION_JSON)
        .content("{\"version\":" + jdbc.sql("SELECT version FROM competition WHERE id = UUID_TO_BIN(:c)").param("c", c.toString()).query(Long.class).single()
            + ",\"visibility\":\"PRIVATE\"}")).andExpect(status().isOk());
    mvc.perform(get("/api/competitions/{id}", c).with(as(bob))).andExpect(status().isOk());
    mvc.perform(get("/api/competitions/mine").with(as(bob))).andExpect(jsonPath("$[*].id").value(hasItem(c.toString())));
    mvc.perform(get("/api/matches/mine").with(as(bob)))
        .andExpect(jsonPath("$[*].id").value(hasItem(competitionMatches(c).getFirst().toString())));
    TestUser zoe = register("Zoe");
    mvc.perform(get("/api/competitions/mine").with(as(zoe))).andExpect(jsonPath("$[*].id").value(not(hasItem(c.toString()))));
  }

  @Test
  void aPickupsAdminPicksBothSides() throws Exception {
    UUID m = createUpcomingPickup(ana, PRIVATE);
    mvc.perform(get("/api/matches/{id}", m).with(as(ana))).andExpect(jsonPath("$.lineupSides").value(containsInAnyOrder("HOME", "AWAY")));
  }
}
