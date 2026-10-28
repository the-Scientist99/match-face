package com.sportsapp.match;

import static com.sportsapp.common.Visibility.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** A pickup with A and B on HOME, C on AWAY. */
class EventApiTest extends IntegrationTest {

  private TestUser ana;
  private UUID match;
  private UUID a;
  private UUID b;
  private UUID c;

  @BeforeEach
  void setUpMatch() throws Exception {
    ana = register("Ana");
    a = createGuest(ana, "A");
    b = createGuest(ana, "B");
    c = createGuest(ana, "C");
    match = createPickup(ana, PRIVATE);
    setLineup(ana, match, a, Side.HOME, b, Side.HOME, c, Side.AWAY);
  }

  /** Alternating keys and values; nulls are left out. */
  private static Map<String, Object> event(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  private ResultActions post(Map<String, Object> body) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/matches/{id}/events", match)
        .with(as(ana)).contentType(APPLICATION_JSON).content(toJson(body)));
  }

  private String firstEventId() {
    return jdbc.sql("SELECT BIN_TO_UUID(id) FROM match_event ORDER BY created_at LIMIT 1").query(String.class).single();
  }

  @Test
  void goalWithAssistAndMinuteUpdatesScore() throws Exception {
    long before = version(match);
    post(event("type", "GOAL", "side", "HOME", "playerId", a, "assistPlayerId", b, "minute", 12))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.score.home").value(1))
        .andExpect(jsonPath("$.score.away").value(0))
        .andExpect(jsonPath("$.events[0].playerName").value("A"))
        .andExpect(jsonPath("$.events[0].assistPlayerName").value("B"))
        .andExpect(jsonPath("$.events[0].minute").value(12));
    assertThat(version(match)).isEqualTo(before + 1);
  }

  @Test
  void unknownScorerGoalCounts() throws Exception {
    post(event("type", "GOAL", "side", "AWAY"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.score.away").value(1))
        .andExpect(jsonPath("$.events[0].playerId").value(nullValue()));
  }

  @Test
  void ownGoalScoresForOtherSide() throws Exception {
    post(event("type", "OWN_GOAL", "side", "HOME", "playerId", a))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.score.home").value(0))
        .andExpect(jsonPath("$.score.away").value(1));
  }

  @Test
  void eventsOrderedByMinuteNullsLast() throws Exception {
    post(event("type", "GOAL", "side", "HOME", "minute", 50));
    post(event("type", "GOAL", "side", "HOME"));
    post(event("type", "GOAL", "side", "AWAY", "minute", 10))
        .andExpect(jsonPath("$.events[*].minute").value(contains(10, 50, null)));
  }

  @Test
  void minuteOutOfRange() throws Exception {
    post(event("type", "GOAL", "side", "HOME", "minute", 151))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"))
        .andExpect(jsonPath("$.errors.minute").value("Max"));
    post(event("type", "GOAL", "side", "HOME", "minute", -1))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.minute").value("Min"));
    post(event("side", "HOME")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.type").value("NotNull"));
  }

  @Test
  void cardNeedsPlayer() throws Exception {
    post(event("type", "YELLOW", "side", "HOME")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EVENT"));
    post(event("type", "MOTM", "side", "HOME")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EVENT"));
  }

  @Test
  void assistRules() throws Exception {
    post(event("type", "YELLOW", "side", "HOME", "playerId", a, "assistPlayerId", b))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EVENT"));
    post(event("type", "GOAL", "side", "HOME", "playerId", a, "assistPlayerId", a))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EVENT"));
    post(event("type", "GOAL", "side", "HOME", "assistPlayerId", b))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EVENT"));
    post(event("type", "OWN_GOAL", "side", "HOME", "playerId", a, "assistPlayerId", b))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EVENT"));
  }

  @Test
  void playerMustBeInLineupOnThatSide() throws Exception {
    post(event("type", "GOAL", "side", "AWAY", "playerId", a))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_LINEUP"));
    post(event("type", "GOAL", "side", "HOME", "playerId", a, "assistPlayerId", c))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_LINEUP"));
    post(event("type", "OWN_GOAL", "side", "AWAY", "playerId", a))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_LINEUP"));
    post(event("type", "YELLOW", "side", "HOME", "playerId", UUID.randomUUID()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_LINEUP"));
  }

  @Test
  void onlyOneMotm() throws Exception {
    post(event("type", "MOTM", "side", "HOME", "playerId", a)).andExpect(status().isCreated());
    post(event("type", "MOTM", "side", "AWAY", "playerId", c))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EVENT"));
    // editing the one MOTM is not a second one
    mvc.perform(patch("/api/matches/{id}/events/{e}", match, firstEventId()).with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(event("type", "MOTM", "side", "AWAY", "playerId", c))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].playerName").value("C"));
  }

  @Test
  void summaryScoreAgreesWithDetailScore() throws Exception {
    post(event("type", "GOAL", "side", "HOME", "playerId", a));
    post(event("type", "OWN_GOAL", "side", "HOME", "playerId", b));
    post(event("type", "GOAL", "side", "AWAY"));
    post(event("type", "YELLOW", "side", "AWAY", "playerId", c));
    mvc.perform(get("/api/matches/{id}", match).with(as(ana)))
        .andExpect(jsonPath("$.score.home").value(1)).andExpect(jsonPath("$.score.away").value(2));
    mvc.perform(get("/api/matches/mine").with(as(ana)))
        .andExpect(jsonPath("$[0].score.home").value(1)).andExpect(jsonPath("$[0].score.away").value(2));
  }

  @Test
  void editAndDeleteEvent() throws Exception {
    post(event("type", "GOAL", "side", "HOME", "playerId", a, "minute", 5));
    String e = firstEventId();
    mvc.perform(patch("/api/matches/{id}/events/{e}", match, e).with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(event("type", "GOAL", "side", "HOME", "playerId", a, "minute", 7))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].minute").value(7));
    mvc.perform(patch("/api/matches/{id}/events/{e}", match, e).with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(event("type", "GOAL", "side", "AWAY", "playerId", a))))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_LINEUP"));
    mvc.perform(delete("/api/matches/{id}/events/{e}", match, e).with(as(ana)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events").value(empty()))
        .andExpect(jsonPath("$.score.home").value(0));
    mvc.perform(delete("/api/matches/{id}/events/{e}", match, e).with(as(ana))).andExpect(status().isNotFound());
  }

  @Test
  void eventOfAnotherMatchIs404() throws Exception {
    UUID other = createPickup(ana, PRIVATE);
    post(event("type", "GOAL", "side", "HOME"));
    mvc.perform(delete("/api/matches/{id}/events/{e}", other, firstEventId()).with(as(ana))).andExpect(status().isNotFound());
  }

  @Test
  void nonAdminCannotAddEvents() throws Exception {
    TestUser zoe = register("Zoe");
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/matches/{id}/events", match)
            .with(as(zoe)).contentType(APPLICATION_JSON).content(toJson(event("type", "GOAL", "side", "HOME"))))
        .andExpect(status().isNotFound());
  }

  private ResultActions putLineup(Object... playerSidePairs) throws Exception {
    List<Map<String, Object>> entries = new java.util.ArrayList<>();
    for (int i = 0; i < playerSidePairs.length; i += 2) entries.add(Map.of("playerId", playerSidePairs[i], "side", playerSidePairs[i + 1]));
    return mvc.perform(put("/api/matches/{id}/lineup", match).with(as(ana)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("entries", entries))));
  }

  @Test
  void removingPlayerWithEventsIsRejected() throws Exception {
    post(event("type", "GOAL", "side", "HOME", "playerId", a));
    putLineup(b, Side.HOME, c, Side.AWAY)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAYER_HAS_EVENTS"));
    mvc.perform(get("/api/matches/{id}", match).with(as(ana))).andExpect(jsonPath("$.lineup.length()").value(3));
    putLineup(a, Side.HOME, c, Side.AWAY).andExpect(status().isOk()); // B has no events, so B may go
  }

  @Test
  void movingPlayerWithEventsIsRejected() throws Exception {
    post(event("type", "GOAL", "side", "HOME", "playerId", a, "assistPlayerId", b));
    putLineup(a, Side.HOME, b, Side.AWAY, c, Side.AWAY)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAYER_HAS_EVENTS"));
  }
}
