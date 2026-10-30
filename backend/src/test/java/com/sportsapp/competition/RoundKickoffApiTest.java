package com.sportsapp.competition;

import static com.sportsapp.common.Visibility.PUBLIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class RoundKickoffApiTest extends IntegrationTest {

  private ResultActions setRound(TestUser u, UUID competition, int round, Instant kickoff) throws Exception {
    return mvc.perform(put("/api/competitions/{id}/rounds/{round}/kickoff", competition, round).with(as(u))
        .contentType(APPLICATION_JSON).content(toJson(Map.of("kickoffAt", kickoff.toString()))));
  }

  private Map<String, Object> row(UUID match) {
    return jdbc.sql("SELECT round, kickoff_at, version FROM sport_match WHERE id = UUID_TO_BIN(:id)").param("id", match.toString())
        .query().singleRow();
  }

  @Test
  void setsTheKickoffOfEveryScheduledMatchInTheRound() throws Exception {
    TestUser ana = register("Ana");
    TestUser zoe = register("Zoe");
    UUID league = createLeague(ana, PUBLIC, createTeam(ana, "A"), createTeam(ana, "B"), createTeam(ana, "C"), createTeam(ana, "D"));
    List<UUID> matches = competitionMatches(league);
    Instant kickoff = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

    setRound(ana, league, 1, kickoff).andExpect(status().isOk());
    for (UUID m : matches) {
      Map<String, Object> r = row(m);
      boolean first = ((Number) r.get("round")).intValue() == 1;
      assertThat(r.get("kickoff_at") != null).isEqualTo(first);
      assertThat(((Number) r.get("version")).longValue()).isEqualTo(first ? 1 : 0);
    }
    mvc.perform(get("/api/matches/{id}", matches.getFirst()))
        .andExpect(jsonPath("$.kickoffAt").value(kickoff.toString()));

    setRound(ana, league, 1, Instant.now().minusSeconds(60))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("KICKOFF_IN_PAST"));
    setRound(zoe, league, 1, kickoff).andExpect(status().isForbidden());
    setRound(ana, league, 9, kickoff).andExpect(status().isNotFound());
  }
}
