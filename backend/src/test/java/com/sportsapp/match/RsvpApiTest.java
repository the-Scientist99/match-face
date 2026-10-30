package com.sportsapp.match;

import static com.sportsapp.common.Visibility.PUBLIC;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import com.sportsapp.common.Visibility;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class RsvpApiTest extends IntegrationTest {

  private ResultActions rsvp(TestUser u, UUID matchId, boolean going) throws Exception {
    return mvc.perform(put("/api/matches/{id}/rsvp", matchId).with(as(u)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("going", going))));
  }

  private UUID teamMatch(TestUser owner, UUID home, UUID away, Visibility visibility) throws Exception {
    String body = mvc.perform(post("/api/matches").with(as(owner)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("homeTeamId", home, "awayTeamId", away, "visibility", visibility,
                "kickoffAt", Instant.now().plusSeconds(3600).toString()))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asString());
  }

  @Test
  void rosterPlayersSeeTheMatchAndAnswer() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    TestUser zoe = register("Zoe");
    TestUser bo = register("Bo");
    UUID alpha = createTeam(ana, "Alpha");
    UUID beta = createTeam(ana, "Beta");
    putOnRoster(alpha, ivan.playerId());
    putOnRoster(beta, zoe.playerId());
    UUID m = teamMatch(ana, alpha, beta, Visibility.PRIVATE);

    mvc.perform(get("/api/matches/{id}", m).with(as(ivan)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canRsvp").value(true))
        .andExpect(jsonPath("$.myRsvp").doesNotExist());
    mvc.perform(get("/api/matches/mine").with(as(ivan))).andExpect(jsonPath("$[*].id").value(contains(m.toString())));
    mvc.perform(get("/api/matches/{id}", m).with(as(bo))).andExpect(status().isNotFound());

    rsvp(ivan, m, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.myRsvp").value(true))
        .andExpect(jsonPath("$.rsvps[0].name").value("Ivan"))
        .andExpect(jsonPath("$.rsvps[0].side").value("HOME"))
        .andExpect(jsonPath("$.version").value(0));
    rsvp(zoe, m, true).andExpect(status().isOk());
    rsvp(ivan, m, false)
        .andExpect(jsonPath("$.rsvps", hasSize(2)))
        .andExpect(jsonPath("$.rsvps[0].going").value(false))
        .andExpect(jsonPath("$.rsvps[1].side").value("AWAY"));
    mvc.perform(get("/api/matches/{id}", m).with(as(ana)))
        .andExpect(jsonPath("$.canRsvp").value(false))
        .andExpect(jsonPath("$.rsvps", hasSize(2)));
    rsvp(ana, m, true).andExpect(status().isForbidden()); // admin, but on neither roster
  }

  @Test
  void onlyRosterPlayersOfScheduledTeamMatches() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    TestUser bo = register("Bo");
    UUID alpha = createTeam(ana, "Alpha");
    putOnRoster(alpha, ivan.playerId());
    UUID m = teamMatch(ana, alpha, createTeam(ana, "Beta"), PUBLIC);
    rsvp(bo, m, true).andExpect(status().isForbidden());
    rsvp(ivan, createPickup(ana, PUBLIC), true).andExpect(status().isForbidden());
    mvc.perform(post("/api/matches/{id}/cancel", m).with(as(ana)).contentType(APPLICATION_JSON).content("{\"version\":0}"))
        .andExpect(status().isOk());
    rsvp(ivan, m, true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATCH_FINISHED"));
    mvc.perform(get("/api/matches/{id}", m).with(as(ivan))).andExpect(jsonPath("$.canRsvp").value(false));
  }
}
