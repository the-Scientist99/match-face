package com.sportsapp.competition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** League fixtures by the circle method. */
public final class RoundRobin {

  private RoundRobin() {}

  /**
   * Rounds of pairings (round number = index + 1). Every pair meets once (twice with {@code doubleRound},
   * the second half mirroring the first with home and away swapped). With an odd count, each round one team
   * has a bye and is simply absent. Home/away balance per team: within 1 for even counts, exact for odd.
   */
  public static List<List<Pairing>> schedule(List<UUID> teams, boolean doubleRound) {
    if (teams.size() < 2) throw new IllegalArgumentException("A round robin needs at least 2 teams");
    List<UUID> list = new ArrayList<>(teams);
    if (list.size() % 2 == 1) list.addFirst(null); // the bye must take the fixed slot, or odd-count balance breaks
    int n = list.size();
    List<List<Pairing>> rounds = new ArrayList<>();
    for (int r = 0; r < n - 1; r++) {
      List<Pairing> round = new ArrayList<>();
      for (int i = 0; i < n / 2; i++) {
        UUID a = list.get(i);
        UUID b = list.get(n - 1 - i);
        if (a == null || b == null) continue;
        boolean aHome = i == 0 ? r % 2 == 0 : i % 2 == 1;
        round.add(aHome ? new Pairing(a, b) : new Pairing(b, a));
      }
      rounds.add(round);
      list.add(1, list.removeLast()); // rotate everything except index 0
    }
    if (doubleRound) {
      int half = rounds.size();
      for (int r = 0; r < half; r++) rounds.add(rounds.get(r).stream().map(p -> new Pairing(p.away(), p.home())).toList());
    }
    return rounds;
  }
}
