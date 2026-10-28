package com.sportsapp.match;

import static com.sportsapp.common.Visibility.PRIVATE;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class LineupApiTest extends IntegrationTest {

  private ResultActions putLineup(TestUser u, UUID matchId, List<Map<String, Object>> entries) throws Exception {
    return mvc.perform(put("/api/matches/{id}/lineup", matchId).with(as(u)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("entries", entries))));
  }

  private static Map<String, Object> entry(UUID player, Side side, boolean inGoal) {
    return Map.of("playerId", player, "side", side, "inGoal", inGoal);
  }

  @Test
  void setLineupFromMyPlayers() throws Exception {
    TestUser ana = register("Ana");
    UUID zed = createGuest(ana, "Zed");
    UUID bo = createGuest(ana, "Bo");
    UUID m = createPickup(ana, PRIVATE);
    putLineup(ana, m, List.of(entry(bo, Side.AWAY, false), entry(zed, Side.HOME, true), entry(ana.playerId(), Side.AWAY, true)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lineup.length()").value(3))
        .andExpect(jsonPath("$.lineup[*].name").value(contains("Zed", "Ana", "Bo")))
        .andExpect(jsonPath("$.lineup[0].side").value("HOME"))
        .andExpect(jsonPath("$.lineup[0].inGoal").value(true))
        .andExpect(jsonPath("$.lineup[2].inGoal").value(false))
        .andExpect(jsonPath("$.version").value(1));
    putLineup(ana, m, List.of(entry(bo, Side.HOME, false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lineup[*].name").value(contains("Bo")));
  }

  @Test
  void lineupRejectsUnavailablePlayer() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID igor = createGuest(ivan, "Igor");
    UUID m = createPickup(ana, PRIVATE);
    putLineup(ana, m, List.of(entry(igor, Side.HOME, false)))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAYER_NOT_AVAILABLE"));
  }

  @Test
  void lineupKeepsExistingPlayersEvenIfNoLongerMine() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID team = createTeam(ana, "Alpha");
    putOnRoster(team, ivan.playerId());
    UUID m = createPickup(ana, PRIVATE);
    setLineup(ana, m, ivan.playerId(), Side.HOME);
    jdbc.sql("DELETE FROM team_player").update(); // ivan leaves ana's roster after the match was set up
    putLineup(ana, m, List.of(entry(ivan.playerId(), Side.HOME, true))).andExpect(status().isOk());
  }

  @Test
  void lineupRejectsDuplicatePlayer() throws Exception {
    TestUser ana = register("Ana");
    UUID bo = createGuest(ana, "Bo");
    UUID m = createPickup(ana, PRIVATE);
    putLineup(ana, m, List.of(entry(bo, Side.HOME, false), entry(bo, Side.AWAY, false)))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_PLAYER"));
  }

  @Test
  void lineupValidation() throws Exception {
    TestUser ana = register("Ana");
    UUID m = createPickup(ana, PRIVATE);
    mvc.perform(put("/api/matches/{id}/lineup", m).with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"entries\":[{\"side\":\"HOME\"}]}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"))
        .andExpect(jsonPath("$.errors['entries[0].playerId']").value("NotNull"));
    UUID bo = createGuest(ana, "Bo");
    mvc.perform(put("/api/matches/{id}/lineup", m).with(as(ana)).contentType(APPLICATION_JSON)
            .content("{\"entries\":[{\"playerId\":\"" + bo + "\",\"side\":\"HOME\"}]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.lineup[0].inGoal").value(false));
  }

  @Test
  void nonAdminCannotSetLineup() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    TestUser zoe = register("Zoe");
    UUID team = createTeam(ana, "Alpha");
    putOnRoster(team, ivan.playerId());
    UUID m = createPickup(ana, PRIVATE);
    setLineup(ana, m, ivan.playerId(), Side.HOME);
    putLineup(ivan, m, List.of(entry(ivan.playerId(), Side.AWAY, false))).andExpect(status().isForbidden());
    putLineup(zoe, m, List.of(entry(zoe.playerId(), Side.AWAY, false))).andExpect(status().isNotFound());
  }
}
