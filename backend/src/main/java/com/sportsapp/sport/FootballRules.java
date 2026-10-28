package com.sportsapp.sport;

import com.sportsapp.match.EventType;
import com.sportsapp.match.Side;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Keep {@code match.ScoreSql} in step with {@link #score}: lists and standings compute the score in SQL. */
public enum FootballRules implements SportRules {
  INSTANCE;

  @Override
  public Set<EventType> allowedEventTypes() {
    return EnumSet.allOf(EventType.class);
  }

  /** A GOAL counts for its side; an OWN_GOAL's side is the scorer's, so it counts for the other one. */
  @Override
  public Score score(List<ScoreEvent> events) {
    int home = 0;
    int away = 0;
    for (ScoreEvent e : events) {
      Side scoredFor = switch (e.type()) {
        case GOAL -> e.side();
        case OWN_GOAL -> e.side().opposite();
        case YELLOW, RED, MOTM -> null;
      };
      if (scoredFor == Side.HOME) home++;
      else if (scoredFor == Side.AWAY) away++;
    }
    return new Score(home, away);
  }

  @Override
  public int points(int goalsFor, int goalsAgainst) {
    return goalsFor > goalsAgainst ? 3 : goalsFor == goalsAgainst ? 1 : 0;
  }

  @Override
  public List<Tiebreaker> tiebreakers() {
    return List.of(Tiebreaker.POINTS, Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR, Tiebreaker.HEAD_TO_HEAD, Tiebreaker.NAME);
  }
}
