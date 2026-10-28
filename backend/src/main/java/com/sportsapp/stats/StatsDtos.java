package com.sportsapp.stats;

import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.match.Side;

public final class StatsDtos {

  private StatsDtos() {}

  public record PlayerTotals(int appearances, int goals, int assists, int ownGoals, int yellowCards, int redCards,
      int cleanSheets, int manOfTheMatch) {}

  /** One finished match the player played in, with their numbers in it. */
  public record PlayerMatchRow(MatchSummary match, Side side, int goals, int assists) {}
}
