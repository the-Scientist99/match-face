package com.sportsapp.competition;

import static com.sportsapp.common.Visibility.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sportsapp.common.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A team change racing a Finish must see the result and refuse, not regenerate the result away. */
class CompetitionConcurrencyTest extends IntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void addTeamThatWaitedOnAFinishSeesTheResult() throws Exception {
    TestUser ana = register("Ana");
    UUID alpha = createTeam(ana, "Alpha");
    UUID beta = createTeam(ana, "Beta");
    UUID gamma = createTeam(ana, "Gamma");
    UUID league = createLeague(ana, PRIVATE, alpha, beta);
    UUID match = competitionMatches(league).getFirst();

    CompletableFuture<Integer> addStatus = new CompletableFuture<>();
    try (Connection finishing = dataSource.getConnection()) {
      finishing.setAutoCommit(false);
      try (PreparedStatement lock = finishing.prepareStatement("SELECT id FROM sport_match WHERE id = UUID_TO_BIN(?) FOR UPDATE")) {
        lock.setString(1, match.toString());
        lock.executeQuery().close(); // a Finish in flight holds the match row
      }
      Thread adder = new Thread(() -> {
        try {
          addStatus.complete(mvc.perform(post("/api/competitions/{id}/teams", league).with(as(ana)).contentType(APPLICATION_JSON)
              .content(toJson(Map.of("teamId", gamma)))).andReturn().getResponse().getStatus());
        } catch (Exception e) {
          addStatus.completeExceptionally(e);
        }
      });
      adder.start();
      Thread.sleep(700); // add-team is now waiting on the match row
      try (PreparedStatement finish = finishing.prepareStatement(
          "UPDATE sport_match SET status = 'FINISHED', version = version + 1 WHERE id = UUID_TO_BIN(?)")) {
        finish.setString(1, match.toString());
        finish.executeUpdate();
      }
      finishing.commit();
    }

    assertThat(addStatus.get(15, TimeUnit.SECONDS)).isEqualTo(409);
    assertThat(jdbc.sql("SELECT COUNT(*) FROM sport_match WHERE status = 'FINISHED'").query(Integer.class).single()).isEqualTo(1);
  }
}
