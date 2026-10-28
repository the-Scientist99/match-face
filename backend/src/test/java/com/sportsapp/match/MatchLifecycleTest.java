package com.sportsapp.match;

import static com.sportsapp.common.Visibility.PRIVATE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class MatchLifecycleTest extends IntegrationTest {

  private TestUser ana;
  private UUID match;

  @BeforeEach
  void setUpMatch() throws Exception {
    ana = register("Ana");
    match = createPickup(ana, PRIVATE);
  }

  private ResultActions action(TestUser u, String action, long version) throws Exception {
    return mvc.perform(post("/api/matches/{id}/" + action, match).with(as(u)).contentType(APPLICATION_JSON)
        .content("{\"version\":" + version + "}"));
  }

  private ResultActions addGoal() throws Exception {
    return mvc.perform(post("/api/matches/{id}/events", match).with(as(ana)).contentType(APPLICATION_JSON)
        .content("{\"type\":\"GOAL\",\"side\":\"HOME\"}"));
  }

  @Test
  void finishThenEditIsBlockedUntilReopen() throws Exception {
    action(ana, "finish", version(match)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINISHED"));
    addGoal().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATCH_FINISHED"));
    mvc.perform(put("/api/matches/{id}/lineup", match).with(as(ana)).contentType(APPLICATION_JSON).content("{\"entries\":[]}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATCH_FINISHED"));
    action(ana, "reopen", version(match)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));
    addGoal().andExpect(status().isCreated());
  }

  @Test
  void finishTwice() throws Exception {
    action(ana, "finish", version(match)).andExpect(status().isOk());
    action(ana, "finish", version(match)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATCH_FINISHED"));
  }

  @Test
  void cancelAndReopen() throws Exception {
    action(ana, "cancel", version(match)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    addGoal().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATCH_FINISHED"));
    action(ana, "finish", version(match)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATCH_FINISHED"));
    action(ana, "reopen", version(match)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));
  }

  @Test
  void reopenScheduledIsANoOp() throws Exception {
    long v = version(match);
    action(ana, "reopen", v).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.version").value(v));
  }

  @Test
  void lifecycleNeedsCurrentVersion() throws Exception {
    long v = version(match);
    addGoal(); // bumps the version
    action(ana, "finish", v).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE"));
    mvc.perform(post("/api/matches/{id}/finish", match).with(as(ana)).contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").value("NotNull"));
  }

  @Test
  void memberCannotFinish() throws Exception {
    TestUser ivan = register("Ivan");
    UUID team = createTeam(ana, "Alpha");
    putOnRoster(team, ivan.playerId());
    setLineup(ana, match, ivan.playerId(), Side.HOME);
    action(ivan, "finish", version(match)).andExpect(status().isForbidden());
    action(ivan, "cancel", version(match)).andExpect(status().isForbidden());
  }
}
