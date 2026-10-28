package com.sportsapp.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  private final MutableClock clock = new MutableClock();
  private final RateLimiter limiter = new RateLimiter(clock);

  @Test
  void allowsTenPerMinutePerActionAndKeyThenRejects() {
    for (int i = 0; i < 10; i++) limiter.check("login", "1.2.3.4");
    assertThatThrownBy(() -> limiter.check("login", "1.2.3.4"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RATE_LIMITED"));
    limiter.check("login", "5.6.7.8");       // other key unaffected
    limiter.check("register", "1.2.3.4");    // other action unaffected
    clock.advance(Duration.ofSeconds(61));
    limiter.check("login", "1.2.3.4");       // window reset
  }
}
