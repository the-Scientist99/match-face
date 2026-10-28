package com.sportsapp.sport;

import static com.sportsapp.match.EventType.GOAL;
import static com.sportsapp.match.EventType.MOTM;
import static com.sportsapp.match.EventType.OWN_GOAL;
import static com.sportsapp.match.EventType.RED;
import static com.sportsapp.match.EventType.YELLOW;
import static com.sportsapp.match.Side.AWAY;
import static com.sportsapp.match.Side.HOME;
import static org.assertj.core.api.Assertions.assertThat;

import com.sportsapp.match.EventType;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class FootballRulesTest {

  private final SportRules rules = SportRules.of(Sport.FOOTBALL);

  @Test
  void ownGoalCountsForOppositeSide() {
    assertThat(rules.score(List.of(new ScoreEvent(GOAL, HOME), new ScoreEvent(OWN_GOAL, HOME), new ScoreEvent(GOAL, AWAY))))
        .isEqualTo(new Score(1, 2));
  }

  @Test
  void cardsAndMotmDoNotScore() {
    assertThat(rules.score(List.of(new ScoreEvent(YELLOW, HOME), new ScoreEvent(RED, AWAY), new ScoreEvent(MOTM, HOME))))
        .isEqualTo(new Score(0, 0));
  }

  @Test
  void emptyIsNilNil() {
    assertThat(rules.score(List.of())).isEqualTo(new Score(0, 0));
  }

  @Test
  void points() {
    assertThat(rules.points(3, 1)).isEqualTo(3);
    assertThat(rules.points(2, 2)).isEqualTo(1);
    assertThat(rules.points(0, 1)).isZero();
  }

  @Test
  void tiebreakerOrder() {
    assertThat(rules.tiebreakers()).containsExactly(
        Tiebreaker.POINTS, Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR, Tiebreaker.HEAD_TO_HEAD, Tiebreaker.NAME);
  }

  @Test
  void allEventTypesAllowed() {
    assertThat(rules.allowedEventTypes()).isEqualTo(EnumSet.allOf(EventType.class));
  }

  @Test
  void sideOpposite() {
    assertThat(HOME.opposite()).isEqualTo(AWAY);
    assertThat(AWAY.opposite()).isEqualTo(HOME);
  }
}
