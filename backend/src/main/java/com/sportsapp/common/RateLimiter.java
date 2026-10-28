package com.sportsapp.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * At most 10 requests per 60-second window for each (action, client key).
 * ponytail: in-memory fixed windows, single instance only (spec 8); entries are never evicted, which is
 * fine at casual-group scale. Move to a shared store with expiry if the app runs on several instances.
 */
@Component
public class RateLimiter {

  private static final int LIMIT = 10;
  private static final Duration WINDOW = Duration.ofSeconds(60);

  private record Window(Instant start, int count) {}

  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimiter(Clock clock) {
    this.clock = clock;
  }

  public void check(String action, String clientKey) {
    Instant now = clock.instant();
    Window w = windows.compute(action + "|" + clientKey, (k, old) ->
        old == null || !now.isBefore(old.start().plus(WINDOW)) ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
    if (w.count() > LIMIT) throw ApiException.tooManyRequests();
  }

  /** Tests only. */
  public void reset() {
    windows.clear();
  }
}
