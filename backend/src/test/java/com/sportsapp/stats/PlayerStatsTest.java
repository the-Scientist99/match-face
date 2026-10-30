package com.sportsapp.stats;

import static com.sportsapp.common.Visibility.PRIVATE;
import static com.sportsapp.common.Visibility.PUBLIC;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import com.sportsapp.match.Side;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class PlayerStatsTest extends IntegrationTest {

  private TestUser ana;
  private UUID a;
  private UUID b;
  private UUID c;

  @BeforeEach
  void setUpPlayers() throws Exception {
    ana = register("Ana");
    a = createGuest(ana, "A");
    b = createGuest(ana, "B");
    c = createGuest(ana, "C");
  }

  private ResultActions profile(UUID player, TestUser viewer) throws Exception {
    return mvc.perform(viewer == null ? get("/api/players/{id}", player) : get("/api/players/{id}", player).with(as(viewer)));
  }

  /** A pickup with A, B on HOME and C on AWAY. */
  private UUID matchWithABvsC(com.sportsapp.common.Visibility v) throws Exception {
    UUID m = createPickup(ana, v);
    setLineup(ana, m, a, Side.HOME, b, Side.HOME, c, Side.AWAY);
    return m;
  }

  @Test
  void totalsFromFinishedMatchesOnly() throws Exception {
    UUID done = matchWithABvsC(PRIVATE);
    addEvent(ana, done, "type", "GOAL", "side", "HOME", "playerId", a);
    addEvent(ana, done, "type", "GOAL", "side", "HOME", "playerId", a);
    finish(ana, done);
    UUID open = matchWithABvsC(PRIVATE);
    addEvent(ana, open, "type", "GOAL", "side", "HOME", "playerId", a);
    profile(a, ana).andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("A"))
        .andExpect(jsonPath("$.totals.goals").value(2))
        .andExpect(jsonPath("$.totals.appearances").value(1))
        .andExpect(jsonPath("$.matches.length()").value(1));
  }

  @Test
  void assistsOwnGoalsCardsMotm() throws Exception {
    UUID m = matchWithABvsC(PRIVATE);
    addEvent(ana, m, "type", "GOAL", "side", "HOME", "playerId", b, "assistPlayerId", a);
    addEvent(ana, m, "type", "OWN_GOAL", "side", "HOME", "playerId", a);
    addEvent(ana, m, "type", "YELLOW", "side", "HOME", "playerId", a);
    addEvent(ana, m, "type", "RED", "side", "AWAY", "playerId", c);
    addEvent(ana, m, "type", "MOTM", "side", "HOME", "playerId", a);
    finish(ana, m);
    profile(a, ana)
        .andExpect(jsonPath("$.totals.goals").value(0))
        .andExpect(jsonPath("$.totals.assists").value(1))
        .andExpect(jsonPath("$.totals.ownGoals").value(1))
        .andExpect(jsonPath("$.totals.yellowCards").value(1))
        .andExpect(jsonPath("$.totals.redCards").value(0))
        .andExpect(jsonPath("$.totals.manOfTheMatch").value(1));
    profile(c, ana).andExpect(jsonPath("$.totals.redCards").value(1));
  }

  @Test
  void cleanSheetRules() throws Exception {
    UUID keeper = createGuest(ana, "K");
    UUID[] matches = new UUID[3];
    for (int i = 0; i < 3; i++) {
      matches[i] = createPickup(ana, PRIVATE);
      mvc.perform(put("/api/matches/{id}/lineup", matches[i]).with(as(ana)).contentType(APPLICATION_JSON).content(toJson(Map.of("entries", List.of(
              Map.of("playerId", keeper, "side", "HOME", "inGoal", true),
              Map.of("playerId", a, "side", "HOME", "inGoal", false),
              Map.of("playerId", c, "side", "AWAY", "inGoal", false))))))
          .andExpect(status().isOk());
    }
    addEvent(ana, matches[0], "type", "GOAL", "side", "HOME", "playerId", a);      // 1-0: clean sheet
    addEvent(ana, matches[1], "type", "GOAL", "side", "AWAY");                     // conceded, unknown scorer
    addEvent(ana, matches[2], "type", "OWN_GOAL", "side", "HOME", "playerId", a);  // conceded through a teammate
    for (UUID m : matches) finish(ana, m);
    profile(keeper, ana).andExpect(jsonPath("$.totals.cleanSheets").value(1)).andExpect(jsonPath("$.totals.appearances").value(3));
    profile(a, ana).andExpect(jsonPath("$.totals.cleanSheets").value(0));
  }

  @Test
  void unknownScorerGoalCreditsNobody() throws Exception {
    UUID m = matchWithABvsC(PRIVATE);
    addEvent(ana, m, "type", "GOAL", "side", "AWAY");
    finish(ana, m);
    profile(c, ana).andExpect(jsonPath("$.totals.goals").value(0)).andExpect(jsonPath("$.matches[0].goals").value(0));
  }

  private void onePublicOnePrivateGoal() throws Exception {
    for (var v : new com.sportsapp.common.Visibility[] {PUBLIC, PRIVATE}) {
      UUID m = matchWithABvsC(v);
      addEvent(ana, m, "type", "GOAL", "side", "HOME", "playerId", a);
      finish(ana, m);
    }
  }

  @Test
  void strangerSeesPublicOnly() throws Exception {
    onePublicOnePrivateGoal();
    TestUser zoe = register("Zoe");
    profile(a, zoe).andExpect(jsonPath("$.totals.goals").value(1)).andExpect(jsonPath("$.matches.length()").value(1));
    profile(a, null).andExpect(status().isOk())
        .andExpect(jsonPath("$.totals.goals").value(1)).andExpect(jsonPath("$.matches.length()").value(1));
  }

  @Test
  void memberSeesSharedPrivateMatch() throws Exception {
    TestUser ivan = register("Ivan");
    UUID team = createTeam(ana, "T");
    putOnRoster(team, ivan.playerId());
    for (var v : new com.sportsapp.common.Visibility[] {PUBLIC, PRIVATE}) {
      UUID m = createPickup(ana, v);
      setLineup(ana, m, a, Side.HOME, ivan.playerId(), Side.AWAY);
      addEvent(ana, m, "type", "GOAL", "side", "HOME", "playerId", a);
      finish(ana, m);
    }
    profile(a, ivan).andExpect(jsonPath("$.totals.goals").value(2)).andExpect(jsonPath("$.matches.length()").value(2));
  }

  @Test
  void ownProfileSeesAll() throws Exception {
    TestUser ivan = register("Ivan");
    UUID team = createTeam(ana, "T");
    putOnRoster(team, ivan.playerId());
    UUID m = createPickup(ana, PRIVATE);
    setLineup(ana, m, ivan.playerId(), Side.HOME, c, Side.AWAY);
    addEvent(ana, m, "type", "GOAL", "side", "HOME", "playerId", ivan.playerId());
    finish(ana, m);
    profile(ivan.playerId(), ivan).andExpect(jsonPath("$.totals.goals").value(1)).andExpect(jsonPath("$.claimed").value(true));
    profile(ivan.playerId(), register("Zoe")).andExpect(jsonPath("$.totals.goals").value(0));
  }

  @Test
  void matchRowsCarryPerMatchNumbers() throws Exception {
    UUID first = matchWithABvsC(PRIVATE);
    addEvent(ana, first, "type", "GOAL", "side", "HOME", "playerId", b, "assistPlayerId", a);
    finish(ana, first);
    UUID second = matchWithABvsC(PRIVATE);
    addEvent(ana, second, "type", "GOAL", "side", "HOME", "playerId", a);
    addEvent(ana, second, "type", "GOAL", "side", "HOME", "playerId", a);
    finish(ana, second);
    profile(a, ana)
        .andExpect(jsonPath("$.matches[*].match.id").value(contains(second.toString(), first.toString())))
        .andExpect(jsonPath("$.matches[0].goals").value(2))
        .andExpect(jsonPath("$.matches[0].assists").value(0))
        .andExpect(jsonPath("$.matches[0].side").value("HOME"))
        .andExpect(jsonPath("$.matches[0].match.score.home").value(2))
        .andExpect(jsonPath("$.matches[1].assists").value(1));
  }

  @Test
  void canInviteOnlyForCreatorOfGuest() throws Exception {
    TestUser ivan = register("Ivan");
    profile(a, ana).andExpect(jsonPath("$.canInvite").value(true)).andExpect(jsonPath("$.canEdit").value(true));
    profile(a, ivan).andExpect(jsonPath("$.canInvite").value(false)).andExpect(jsonPath("$.canEdit").value(false));
    profile(ana.playerId(), ana).andExpect(jsonPath("$.canInvite").value(false)).andExpect(jsonPath("$.canEdit").value(true));
    profile(a, null).andExpect(jsonPath("$.canInvite").value(false));
  }

  @Test
  void upcomingShowsLineupMatchesTheViewerCanSee() throws Exception {
    UUID open = createUpcomingPickup(ana, PUBLIC);
    setLineup(ana, open, a, Side.AWAY);
    UUID hidden = createUpcomingPickup(ana, PRIVATE);
    setLineup(ana, hidden, a, Side.HOME);
    UUID done = createPickup(ana, PUBLIC);
    setLineup(ana, done, a, Side.HOME);
    finish(ana, done);
    profile(a, ana).andExpect(jsonPath("$.upcoming.length()").value(2));
    profile(a, null).andExpect(jsonPath("$.upcoming.length()").value(1))
        .andExpect(jsonPath("$.upcoming[0].match.id").value(open.toString()))
        .andExpect(jsonPath("$.upcoming[0].side").value("AWAY"));
  }

  @Test
  void teamsAreOnlyThoseTheViewerMayRead() throws Exception {
    UUID team = createTeam(ana, "Blue Harbour", a);
    profile(a, ana).andExpect(jsonPath("$.teams[0].id").value(team.toString()))
        .andExpect(jsonPath("$.teams[0].name").value("Blue Harbour"));
    profile(a, register("Ivan")).andExpect(jsonPath("$.teams.length()").value(0));
    profile(a, null).andExpect(jsonPath("$.teams.length()").value(0));
  }
}
