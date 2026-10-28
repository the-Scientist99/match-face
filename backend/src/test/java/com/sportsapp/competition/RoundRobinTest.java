package com.sportsapp.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoundRobinTest {

  private static List<UUID> teams(int n) {
    return IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).toList();
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
  void singleRoundProperties(int n) {
    List<UUID> teams = teams(n);
    List<List<Pairing>> rounds = RoundRobin.schedule(teams, false);
    assertThat(rounds).hasSize(n % 2 == 0 ? n - 1 : n);

    Set<Set<UUID>> pairs = new HashSet<>();
    Map<UUID, Integer> home = new HashMap<>();
    Map<UUID, Integer> away = new HashMap<>();
    for (List<Pairing> round : rounds) {
      Set<UUID> seen = new HashSet<>();
      for (Pairing p : round) {
        assertThat(seen.add(p.home())).as("team twice in a round").isTrue();
        assertThat(seen.add(p.away())).as("team twice in a round").isTrue();
        assertThat(pairs.add(Set.of(p.home(), p.away()))).as("pair meets twice").isTrue();
        home.merge(p.home(), 1, Integer::sum);
        away.merge(p.away(), 1, Integer::sum);
      }
    }
    assertThat(pairs).hasSize(n * (n - 1) / 2);
    for (UUID t : teams) {
      int diff = Math.abs(home.getOrDefault(t, 0) - away.getOrDefault(t, 0));
      if (n % 2 == 0) assertThat(diff).isLessThanOrEqualTo(1);
      else assertThat(diff).isZero();
    }
  }

  @Test
  void doubleRoundMirrorsFirstHalf() {
    List<List<Pairing>> rounds = RoundRobin.schedule(teams(4), true);
    assertThat(rounds).hasSize(6);
    for (int k = 0; k < 3; k++) {
      assertThat(rounds.get(k + 3)).containsExactlyElementsOf(
          rounds.get(k).stream().map(p -> new Pairing(p.away(), p.home())).toList());
    }
  }

  @Test
  void oddCountGivesOneByePerRound() {
    List<UUID> teams = teams(5);
    List<List<Pairing>> rounds = RoundRobin.schedule(teams, false);
    Map<UUID, Integer> absent = new HashMap<>();
    for (List<Pairing> round : rounds) {
      assertThat(round).hasSize(2);
      Set<UUID> playing = new HashSet<>();
      round.forEach(p -> { playing.add(p.home()); playing.add(p.away()); });
      teams.stream().filter(t -> !playing.contains(t)).forEach(t -> absent.merge(t, 1, Integer::sum));
    }
    assertThat(absent).hasSize(5).allSatisfy((t, count) -> assertThat(count).isEqualTo(1));
  }

  @Test
  void lessThanTwoTeamsRejected() {
    assertThatThrownBy(() -> RoundRobin.schedule(teams(1), false)).isInstanceOf(IllegalArgumentException.class);
  }
}
