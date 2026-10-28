package com.sportsapp.competition;

import com.sportsapp.match.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Single-elimination brackets. The match at {@code (round, position)} feeds {@code (round + 1, position / 2)},
 * into the HOME slot from an even position and AWAY from an odd one. Pure: no database.
 */
public final class Bracket {

  public static final int MAX_TEAMS = 64;

  private Bracket() {}

  /** Standard bracket positions: seed 1 and 2 can only meet in the final, and so on down. */
  public static List<Integer> seedOrder(int size) {
    List<Integer> order = List.of(1, 2);
    for (int m = 2; m < size; m *= 2) {
      List<Integer> next = new ArrayList<>();
      for (int s : order) {
        next.add(s);
        next.add(2 * m + 1 - s);
      }
      order = next;
    }
    return order;
  }

  public static Side nextSlot(int position) {
    return position % 2 == 0 ? Side.HOME : Side.AWAY;
  }

  /**
   * Seeded teams rank first by seed (gaps don't matter), then the unseeded in random order. The bracket is
   * the smallest power of two that fits; the missing teams are byes, which go to the top ranks: those teams
   * start in round 2 with no round-1 match. Every match is planned up front, later ones without teams.
   */
  public static BracketPlan plan(List<SeededTeam> teams, Random random) {
    int n = teams.size();
    if (n < 2 || n > MAX_TEAMS) throw new IllegalArgumentException("A cup needs 2.." + MAX_TEAMS + " teams, got " + n);
    List<SeededTeam> ranked = new ArrayList<>(teams.stream().filter(t -> t.seed() != null)
        .sorted(Comparator.comparing(SeededTeam::seed)).toList());
    List<SeededTeam> unseeded = new ArrayList<>(teams.stream().filter(t -> t.seed() == null).toList());
    Collections.shuffle(unseeded, random);
    ranked.addAll(unseeded);

    int size = Integer.highestOneBit(n - 1) << 1;
    int rounds = Integer.numberOfTrailingZeros(size);
    // the later rounds, filled in as byes are placed
    UUID[][] home = new UUID[rounds + 1][];
    UUID[][] away = new UUID[rounds + 1][];
    for (int r = 2; r <= rounds; r++) {
      home[r] = new UUID[size >> r];
      away[r] = new UUID[size >> r];
    }

    List<PlannedMatch> matches = new ArrayList<>();
    List<Integer> order = seedOrder(size);
    for (int p = 0; p < size / 2; p++) {
      int a = order.get(2 * p);
      int b = order.get(2 * p + 1);
      UUID first = ranked.get(a - 1).teamId();
      if (b > n) {
        (nextSlot(p) == Side.HOME ? home : away)[2][p / 2] = first; // a bye: straight into round 2
      } else {
        matches.add(new PlannedMatch(1, p, first, ranked.get(b - 1).teamId()));
      }
    }
    for (int r = 2; r <= rounds; r++) {
      for (int p = 0; p < size >> r; p++) matches.add(new PlannedMatch(r, p, home[r][p], away[r][p]));
    }
    return new BracketPlan(rounds, matches);
  }
}
