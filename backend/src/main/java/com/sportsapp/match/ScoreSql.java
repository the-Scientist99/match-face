package com.sportsapp.match;

/**
 * The SQL twin of {@code FootballRules.score}, for lists, standings and brackets. Both expressions expect
 * {@code LEFT JOIN match_event e ON e.match_id = m.id} and {@code GROUP BY m.id}.
 */
public final class ScoreSql {

  private ScoreSql() {}

  public static final String HOME_GOALS =
      "SUM(CASE WHEN (e.type = 'GOAL' AND e.side = 'HOME') OR (e.type = 'OWN_GOAL' AND e.side = 'AWAY') THEN 1 ELSE 0 END)";

  public static final String AWAY_GOALS =
      "SUM(CASE WHEN (e.type = 'GOAL' AND e.side = 'AWAY') OR (e.type = 'OWN_GOAL' AND e.side = 'HOME') THEN 1 ELSE 0 END)";
}
