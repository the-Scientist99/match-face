package com.sportsapp.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class TeamApiTest extends IntegrationTest {

  private ResultActions setRoster(TestUser u, UUID teamId, UUID... playerIds) throws Exception {
    return mvc.perform(put("/api/teams/{id}/players", teamId).with(as(u)).contentType(APPLICATION_JSON)
        .content(toJson(Map.of("playerIds", List.of(playerIds)))));
  }

  private List<String> rosterNames(UUID teamId) {
    return jdbc.sql("SELECT p.name FROM team_player tp JOIN player p ON p.id = tp.player_id WHERE tp.team_id = UUID_TO_BIN(:t) ORDER BY p.name")
        .param("t", teamId.toString()).query(String.class).list();
  }

  @Test
  void createTeamGrantsOwner() throws Exception {
    TestUser ana = register("Ana");
    mvc.perform(post("/api/teams").with(as(ana)).contentType(APPLICATION_JSON).content("{\"name\":\" Alpha \"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Alpha"))
        .andExpect(jsonPath("$.sport").value("FOOTBALL"))
        .andExpect(jsonPath("$.canAdmin").value(true))
        .andExpect(jsonPath("$.players").value(empty()));
    assertThat(jdbc.sql("SELECT CONCAT(resource_type, '/', role, '/', BIN_TO_UUID(user_id)) FROM admin_grant").query(String.class).single())
        .isEqualTo("TEAM/OWNER/" + ana.id());
  }

  @Test
  void createTeamRequiresAuthAndName() throws Exception {
    mvc.perform(post("/api/teams").contentType(APPLICATION_JSON).content("{\"name\":\"Alpha\"}")).andExpect(status().isUnauthorized());
    TestUser ana = register("Ana");
    mvc.perform(post("/api/teams").with(as(ana)).contentType(APPLICATION_JSON).content("{\"name\":\" \"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").value("NotBlank"));
  }

  @Test
  void setRosterFromMyPlayers() throws Exception {
    TestUser ana = register("Ana");
    UUID zed = createGuest(ana, "Zed");
    UUID bo = createGuest(ana, "Bo");
    UUID team = createTeam(ana, "Alpha");
    setRoster(ana, team, zed, bo, ana.playerId(), bo)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.players.length()").value(3))
        .andExpect(jsonPath("$.players[*].name").value(contains("Ana", "Bo", "Zed")))
        .andExpect(jsonPath("$.players[0].claimed").value(true))
        .andExpect(jsonPath("$.players[1].claimed").value(false));
    setRoster(ana, team, bo).andExpect(status().isOk()).andExpect(jsonPath("$.players[*].name").value(contains("Bo")));
    assertThat(rosterNames(team)).containsExactly("Bo");
  }

  @Test
  void rosterRejectsPlayerNotInMyPlayers() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID bo = createGuest(ana, "Bo");
    UUID ivansGuest = createGuest(ivan, "Igor");
    UUID team = createTeam(ana, "Alpha", bo);
    setRoster(ana, team, bo, ivansGuest)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PLAYER_NOT_AVAILABLE"));
    setRoster(ana, team, UUID.randomUUID()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PLAYER_NOT_AVAILABLE"));
    assertThat(rosterNames(team)).containsExactly("Bo");
  }

  @Test
  void rosterKeepsExistingMembers() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID bo = createGuest(ana, "Bo");
    UUID ivansGuest = createGuest(ivan, "Igor");
    UUID team = createTeam(ana, "Alpha");
    putOnRoster(team, ivansGuest); // e.g. a roster ana inherited; being team admin puts it in her players
    setRoster(ana, team, ivansGuest, bo).andExpect(status().isOk())
        .andExpect(jsonPath("$.players[*].name").value(contains("Bo", "Igor")));
  }

  @Test
  void rosterMemberCanReadNotAdmin() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID team = createTeam(ana, "Alpha");
    putOnRoster(team, ivan.playerId()); // only a claim (M4) puts a registered user's player on someone's roster
    mvc.perform(get("/api/teams/{id}", team).with(as(ivan)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canAdmin").value(false))
        .andExpect(jsonPath("$.players[*].name").value(contains("Ivan")));
    mvc.perform(patch("/api/teams/{id}", team).with(as(ivan)).contentType(APPLICATION_JSON).content("{\"name\":\"Mine\"}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    setRoster(ivan, team, ivan.playerId()).andExpect(status().isForbidden());
  }

  @Test
  void adminRenamesTeam() throws Exception {
    TestUser ana = register("Ana");
    UUID team = createTeam(ana, "Alpha");
    mvc.perform(patch("/api/teams/{id}", team).with(as(ana)).contentType(APPLICATION_JSON).content("{\"name\":\"Beta\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Beta"));
  }

  @Test
  void strangerAndAnonymousGet404() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID team = createTeam(ana, "Alpha");
    mvc.perform(get("/api/teams/{id}", team).with(as(ivan))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(get("/api/teams/{id}", team)).andExpect(status().isNotFound());
    mvc.perform(get("/api/teams/{id}", UUID.randomUUID()).with(as(ana))).andExpect(status().isNotFound());
  }

  @Test
  void strangerPatchIs404Not403() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID team = createTeam(ana, "Alpha");
    mvc.perform(patch("/api/teams/{id}", team).with(as(ivan)).contentType(APPLICATION_JSON).content("{\"name\":\"Mine\"}"))
        .andExpect(status().isNotFound());
    setRoster(ivan, team, ivan.playerId()).andExpect(status().isNotFound());
  }

  @Test
  void mineListsAdminAndMemberTeams() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    TestUser zoe = register("Zoe");
    UUID anas = createTeam(ana, "Alpha");
    putOnRoster(anas, ivan.playerId());
    createTeam(ivan, "Zebras");
    createTeam(ivan, "Čelik");
    createTeam(ivan, "Cvijet"); // MySQL's ai_ci would put Čelik first, the alphabet puts C before Č
    createTeam(zoe, "Unrelated");
    mvc.perform(get("/api/teams/mine").with(as(ivan)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name").value(contains("Alpha", "Cvijet", "Čelik", "Zebras")));
    mvc.perform(get("/api/teams/mine")).andExpect(status().isUnauthorized());
  }
}
