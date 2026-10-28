package com.sportsapp.stats;

import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.match.Side;
import java.util.List;
import java.util.UUID;

public final class StatsDtos {

  private StatsDtos() {}

  public record PlayerTotals(int appearances, int goals, int assists, int ownGoals, int yellowCards, int redCards,
      int cleanSheets, int manOfTheMatch) {}

  public record StatLeader(UUID playerId, String name, int value) {}

  public record CardRow(UUID playerId, String name, int yellow, int red) {}

  /** Top 10 of each list, from finished matches only. */
  public record CompetitionStatsResponse(List<StatLeader> topScorers, List<StatLeader> topAssists, List<CardRow> cards) {}

  /** One finished match the player played in, with their numbers in it. */
  public record PlayerMatchRow(MatchSummary match, Side side, int goals, int assists) {}
}
