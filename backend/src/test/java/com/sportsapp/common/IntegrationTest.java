package com.sportsapp.common;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcClient jdbc;
  @Autowired protected JsonMapper json;

  @Autowired private DataSource dataSource;
  @Autowired private RateLimiter rateLimiter;

  protected record TestUser(UUID id, UUID playerId, String token) {}

  @BeforeEach
  void truncateAllTables() {
    TestDatabase.truncateAll(dataSource);
    rateLimiter.reset();
  }

  /** Registers {@code <name lowercased>@test.dev} with password {@code password123} and locale {@code en}. */
  protected TestUser register(String name) throws Exception {
    String body = mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(toJson(Map.of(
            "email", name.toLowerCase() + "@test.dev", "password", "password123", "displayName", name, "locale", "en"))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    JsonNode n = json.readTree(body);
    return new TestUser(UUID.fromString(n.get("user").get("id").asString()),
        UUID.fromString(n.get("user").get("playerId").asString()), n.get("accessToken").asString());
  }

  protected UUID createGuest(TestUser owner, String name) throws Exception {
    String body = mvc.perform(post("/api/players").with(as(owner)).contentType(APPLICATION_JSON).content(toJson(Map.of("name", name))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asString());
  }

  /** Creates a team owned by {@code owner}, with the given roster when any ids are passed. */
  protected UUID createTeam(TestUser owner, String name, UUID... playerIds) throws Exception {
    String body = mvc.perform(post("/api/teams").with(as(owner)).contentType(APPLICATION_JSON).content(toJson(Map.of("name", name))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    UUID id = UUID.fromString(json.readTree(body).get("id").asString());
    if (playerIds.length > 0) {
      mvc.perform(put("/api/teams/{id}/players", id).with(as(owner)).contentType(APPLICATION_JSON)
              .content(toJson(Map.of("playerIds", List.of(playerIds)))))
          .andExpect(status().isOk());
    }
    return id;
  }

  /** A pickup match "Bibs" vs "Non-bibs". */
  protected UUID createPickup(TestUser owner, Visibility visibility) throws Exception {
    String body = mvc.perform(post("/api/matches").with(as(owner)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("homeLabel", "Bibs", "awayLabel", "Non-bibs", "visibility", visibility))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asString());
  }

  /** {@code playerIdSidePairs}: alternating player UUID and {@code Side}; nobody in goal. */
  protected void setLineup(TestUser owner, UUID matchId, Object... playerIdSidePairs) throws Exception {
    List<Map<String, Object>> entries = new ArrayList<>();
    for (int i = 0; i < playerIdSidePairs.length; i += 2) {
      entries.add(Map.of("playerId", playerIdSidePairs[i], "side", playerIdSidePairs[i + 1], "inGoal", false));
    }
    mvc.perform(put("/api/matches/{id}/lineup", matchId).with(as(owner)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("entries", entries))))
        .andExpect(status().isOk());
  }

  /** {@code keyValues}: alternating event field names and values, e.g. "type", "GOAL", "side", "HOME", "playerId", id. */
  protected void addEvent(TestUser owner, UUID matchId, Object... keyValues) throws Exception {
    Map<String, Object> body = new java.util.HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) body.put((String) keyValues[i], keyValues[i + 1]);
    mvc.perform(post("/api/matches/{id}/events", matchId).with(as(owner)).contentType(APPLICATION_JSON).content(toJson(body)))
        .andExpect(status().isCreated());
  }

  protected void finish(TestUser owner, UUID matchId) throws Exception {
    mvc.perform(post("/api/matches/{id}/finish", matchId).with(as(owner)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("version", version(matchId)))))
        .andExpect(status().isOk());
  }

  /** A league named "League" with the given teams, fixtures generated. */
  protected UUID createLeague(TestUser owner, Visibility visibility, UUID... teamIds) throws Exception {
    String body = mvc.perform(post("/api/competitions").with(as(owner)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("name", "League", "type", "LEAGUE", "visibility", visibility, "doubleRoundRobin", false))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    UUID id = UUID.fromString(json.readTree(body).get("id").asString());
    for (UUID team : teamIds) {
      mvc.perform(post("/api/competitions/{id}/teams", id).with(as(owner)).contentType(APPLICATION_JSON)
              .content(toJson(Map.of("teamId", team))))
          .andExpect(status().isOk());
    }
    mvc.perform(post("/api/competitions/{id}/generate", id).with(as(owner))).andExpect(status().isOk());
    return id;
  }

  /** A cup named "Cup"; teams seeded 1..n in argument order; bracket generated. */
  protected UUID createCup(TestUser owner, Visibility visibility, UUID... teamIdsInSeedOrder) throws Exception {
    String body = mvc.perform(post("/api/competitions").with(as(owner)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("name", "Cup", "type", "CUP", "visibility", visibility))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    UUID id = UUID.fromString(json.readTree(body).get("id").asString());
    for (int i = 0; i < teamIdsInSeedOrder.length; i++) {
      mvc.perform(post("/api/competitions/{id}/teams", id).with(as(owner)).contentType(APPLICATION_JSON)
              .content(toJson(Map.of("teamId", teamIdsInSeedOrder[i], "seed", i + 1))))
          .andExpect(status().isOk());
    }
    mvc.perform(post("/api/competitions/{id}/generate", id).with(as(owner))).andExpect(status().isOk());
    return id;
  }

  /** The competition's match ids by round, then creation. */
  protected List<UUID> competitionMatches(UUID competitionId) {
    return jdbc.sql("""
            SELECT BIN_TO_UUID(m.id) FROM sport_match m JOIN stage s ON s.id = m.stage_id
            WHERE s.competition_id = UUID_TO_BIN(:c) ORDER BY m.round, m.created_at, m.id
            """)
        .param("c", competitionId.toString()).query(UUID.class).list();
  }

  protected long version(UUID matchId) {
    return jdbc.sql("SELECT version FROM sport_match WHERE id = UUID_TO_BIN(:id)").param("id", matchId.toString())
        .query(Long.class).single();
  }

  /** Puts a player on a roster directly, e.g. a registered user's own player, which only a claim (M4) does. */
  protected void putOnRoster(UUID teamId, UUID playerId) {
    jdbc.sql("INSERT INTO team_player (id, created_at, team_id, player_id) VALUES (UUID_TO_BIN(UUID()), NOW(6), UUID_TO_BIN(:t), UUID_TO_BIN(:p))")
        .param("t", teamId.toString()).param("p", playerId.toString()).update();
  }

  protected RequestPostProcessor as(TestUser u) {
    return req -> {
      req.addHeader("Authorization", "Bearer " + u.token());
      return req;
    };
  }

  protected String toJson(Object body) {
    return json.writeValueAsString(body);
  }
}
