package com.sportsapp.match;

import static com.sportsapp.common.Visibility.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.sportsapp.common.ApiException;
import com.sportsapp.common.IntegrationTest;
import com.sportsapp.match.MatchDtos.EventRequest;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two writes to one match at once: the second waits on the match row lock and must then validate against
 * what the first committed, not a snapshot from before it waited.
 */
class MatchConcurrencyTest extends IntegrationTest {

  @Autowired MatchService service;
  @Autowired TransactionTemplate tx;

  @Test
  void aWriteThatWaitedForTheLockSeesTheCommittedMotm() throws Exception {
    TestUser ana = register("Ana");
    UUID a = createGuest(ana, "A");
    UUID c = createGuest(ana, "C");
    UUID match = createPickup(ana, PRIVATE);
    setLineup(ana, match, a, Side.HOME, c, Side.AWAY);

    CompletableFuture<Throwable> second = new CompletableFuture<>();
    tx.executeWithoutResult(s -> {
      service.addEvent(ana.id(), match, new EventRequest(EventType.MOTM, Side.HOME, a, null, null)); // holds the lock, uncommitted
      Thread waiter = new Thread(() -> {
        try {
          service.addEvent(ana.id(), match, new EventRequest(EventType.MOTM, Side.AWAY, c, null, null));
          second.complete(null);
        } catch (Throwable t) {
          second.complete(t);
        }
      });
      waiter.start();
      sleep(700); // the waiter is now blocked on the row lock
    });

    Throwable result = second.get(10, TimeUnit.SECONDS);
    assertThat(result).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_EVENT"));
    assertThat(jdbc.sql("SELECT COUNT(*) FROM match_event WHERE type = 'MOTM'").query(Integer.class).single()).isEqualTo(1);
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
