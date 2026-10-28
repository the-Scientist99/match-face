package com.sportsapp.player;

import static com.sportsapp.common.Visibility.PRIVATE;
import static com.sportsapp.common.Visibility.PUBLIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import com.sportsapp.common.Tokens;
import com.sportsapp.match.Side;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class ClaimApiTest extends IntegrationTest {

  private TestUser ana;
  private TestUser ivan;
  private UUID guest;

  @BeforeEach
  void setUpGuest() throws Exception {
    ana = register("Ana");
    ivan = register("Ivan");
    guest = createGuest(ana, "Marko");
  }

  private ResultActions invite(TestUser u, UUID playerId) throws Exception {
    return mvc.perform(post("/api/players/{id}/claims", playerId).with(as(u)));
  }

  private String token(TestUser u, UUID playerId) throws Exception {
    String body = invite(u, playerId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("token").asString();
  }

  private ResultActions preview(String token) throws Exception {
    return mvc.perform(get("/api/players/claims/{token}", token));
  }

  private ResultActions claim(TestUser u, String token) throws Exception {
    return mvc.perform(post("/api/players/claims/{token}", token).with(as(u)));
  }

  private int count(String sql, UUID id) {
    return jdbc.sql(sql).param("id", id.toString()).query(Integer.class).single();
  }

  @Test
  void creatorCreatesInvite() throws Exception {
    String body = invite(ana, guest).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String token = json.readTree(body).get("token").asString();
    Instant expires = Instant.parse(json.readTree(body).get("expiresAt").asString());
    assertThat(token).matches("[A-Za-z0-9_-]{43}");
    assertThat(expires).isBetween(Instant.now().plus(Duration.ofDays(14)).minusSeconds(60), Instant.now().plus(Duration.ofDays(14)).plusSeconds(60));
    assertThat(jdbc.sql("SELECT token_hash FROM player_claim").query(String.class).single())
        .isEqualTo(Tokens.sha256Hex(token)).isNotEqualTo(token);
  }

  @Test
  void onlyCreatorOfUnclaimedGuest() throws Exception {
    invite(ivan, guest).andExpect(status().isForbidden());
    invite(ana, ana.playerId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_INVALID"));
    invite(ana, ivan.playerId()).andExpect(status().isForbidden());
    invite(ana, UUID.randomUUID()).andExpect(status().isNotFound());
    mvc.perform(post("/api/players/{id}/claims", guest)).andExpect(status().isUnauthorized());
  }

  @Test
  void newInviteSupersedesOld() throws Exception {
    String t1 = token(ana, guest);
    String t2 = token(ana, guest);
    preview(t1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_INVALID"));
    preview(t2).andExpect(status().isOk()).andExpect(jsonPath("$.playerName").value("Marko"));
  }

  @Test
  void previewCountsAllFinishedMatches() throws Exception {
    UUID other = createGuest(ana, "Other");
    for (var v : new com.sportsapp.common.Visibility[] {PRIVATE, PUBLIC}) {
      UUID m = createPickup(ana, v);
      setLineup(ana, m, guest, Side.HOME, other, Side.AWAY);
      addEvent(ana, m, "type", "GOAL", "side", "HOME", "playerId", guest);
      if (v == PRIVATE) addEvent(ana, m, "type", "GOAL", "side", "HOME", "playerId", guest);
      finish(ana, m);
    }
    preview(token(ana, guest)).andExpect(status().isOk())
        .andExpect(jsonPath("$.playerId").value(guest.toString()))
        .andExpect(jsonPath("$.appearances").value(2))
        .andExpect(jsonPath("$.goals").value(3));
  }

  @Test
  void claimMergesHistory() throws Exception {
    UUID other = createGuest(ana, "Other");
    UUID team = createTeam(ana, "T", guest);
    UUID privateMatch = createPickup(ana, PRIVATE);
    setLineup(ana, privateMatch, guest, Side.HOME, other, Side.AWAY);
    addEvent(ana, privateMatch, "type", "GOAL", "side", "HOME", "playerId", guest);
    finish(ana, privateMatch);
    UUID second = createPickup(ana, PRIVATE);
    setLineup(ana, second, guest, Side.AWAY, other, Side.AWAY);
    addEvent(ana, second, "type", "GOAL", "side", "AWAY", "playerId", other, "assistPlayerId", guest);
    finish(ana, second);
    mvc.perform(get("/api/matches/{id}", privateMatch).with(as(ivan))).andExpect(status().isNotFound());

    claim(ivan, token(ana, guest)).andExpect(status().isOk()).andExpect(jsonPath("$.playerId").value(ivan.playerId().toString()));

    assertThat(count("SELECT COUNT(*) FROM player WHERE id = UUID_TO_BIN(:id)", guest)).isZero();
    assertThat(count("SELECT COUNT(*) FROM player_claim WHERE player_id = UUID_TO_BIN(:id)", guest)).isZero();
    mvc.perform(get("/api/players/{id}", ivan.playerId()).with(as(ivan)))
        .andExpect(jsonPath("$.totals.appearances").value(2))
        .andExpect(jsonPath("$.totals.goals").value(1))
        .andExpect(jsonPath("$.totals.assists").value(1));
    assertThat(count("SELECT COUNT(*) FROM team_player WHERE player_id = UUID_TO_BIN(:id)", ivan.playerId())).isEqualTo(1);
    mvc.perform(get("/api/teams/{id}", team).with(as(ivan))).andExpect(status().isOk());
    mvc.perform(get("/api/matches/{id}", privateMatch).with(as(ivan))).andExpect(status().isOk());
  }

  @Test
  void claimDeduplicatesRoster() throws Exception {
    UUID team = createTeam(ana, "T", guest);
    putOnRoster(team, ivan.playerId());
    claim(ivan, token(ana, guest)).andExpect(status().isOk());
    assertThat(jdbc.sql("SELECT COUNT(*) FROM team_player").query(Integer.class).single()).isEqualTo(1);
  }

  @Test
  void mergeConflictWhenBothInSameMatch() throws Exception {
    UUID team = createTeam(ana, "T");
    putOnRoster(team, ivan.playerId());
    UUID m = createPickup(ana, PRIVATE);
    setLineup(ana, m, guest, Side.HOME, ivan.playerId(), Side.AWAY);
    addEvent(ana, m, "type", "GOAL", "side", "HOME", "playerId", guest);
    String token = token(ana, guest);
    claim(ivan, token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MERGE_CONFLICT"));
    assertThat(count("SELECT COUNT(*) FROM player WHERE id = UUID_TO_BIN(:id)", guest)).isEqualTo(1);
    assertThat(count("SELECT COUNT(*) FROM match_event WHERE player_id = UUID_TO_BIN(:id)", guest)).isEqualTo(1);
    preview(token).andExpect(status().isOk());
  }

  @Test
  void tokenSingleUse() throws Exception {
    String token = token(ana, guest);
    claim(ivan, token).andExpect(status().isOk());
    claim(ivan, token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_INVALID"));
    preview(token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_INVALID"));
  }

  @Test
  void expiredToken() throws Exception {
    String token = token(ana, guest);
    jdbc.sql("UPDATE player_claim SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND").update();
    preview(token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_INVALID"));
    claim(ivan, token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_INVALID"));
    preview("not-a-real-token").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_INVALID"));
  }

  @Test
  void claimingAPlayerThatIsAlreadyYours() throws Exception {
    // Review Focus 5: a claim row pointing at a linked player (only reachable through a bug or old data)
    String token = Tokens.newToken();
    jdbc.sql("INSERT INTO player_claim (id, created_at, player_id, token_hash, expires_at) "
            + "VALUES (UUID_TO_BIN(UUID()), NOW(6), UUID_TO_BIN(:p), :h, NOW(6) + INTERVAL 1 DAY)")
        .param("p", ivan.playerId().toString()).param("h", Tokens.sha256Hex(token)).update();
    claim(ivan, token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_INVALID"));
    claim(ana, token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_INVALID"));
    assertThat(count("SELECT COUNT(*) FROM player WHERE id = UUID_TO_BIN(:id)", ivan.playerId())).isEqualTo(1);
  }

  @Test
  void claimRequiresAuth() throws Exception {
    mvc.perform(post("/api/players/claims/{token}", token(ana, guest))).andExpect(status().isUnauthorized());
  }

  @Test
  void claimIsRateLimited() throws Exception {
    for (int i = 0; i < 10; i++) claim(ivan, "bad-" + i).andExpect(status().isConflict());
    claim(ivan, "bad-x").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }
}
