package com.sportsapp.sport;

import com.sportsapp.match.EventType;
import java.util.List;
import java.util.Set;

/** What differs between sports. Clean sheets live in the stats SQL (M4), which is football-specific anyway. */
public interface SportRules {

  Set<EventType> allowedEventTypes();

  Score score(List<ScoreEvent> events);

  int points(int goalsFor, int goalsAgainst);

  List<Tiebreaker> tiebreakers();

  static SportRules of(Sport sport) {
    return switch (sport) {
      case FOOTBALL -> FootballRules.INSTANCE;
    };
  }
}
