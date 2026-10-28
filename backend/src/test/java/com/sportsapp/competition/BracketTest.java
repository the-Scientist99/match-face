package com.sportsapp.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BracketTest {

  /** Teams with seeds 1..n. */
  private static List<SeededTeam> seeded(int n) {
    return IntStream.rangeClosed(1, n).mapToObj(s -> new SeededTeam(UUID.randomUUID(), s)).toList();
  }

  private static List<SeededTeam> unseeded(int n) {
    return IntStream.range(0, n).mapToObj(i -> new SeededTeam(UUID.randomUUID(), null)).toList();
  }

  private static PlannedMatch at(BracketPlan plan, int round, int position) {
    return plan.matches().stream().filter(m -> m.round() == round && m.position() == position).findFirst().orElse(null);
  }

  /** Teams in round-1 matches plus teams pre-placed (byes) in round 2. */
  private static List<UUID> placed(BracketPlan plan) {
    List<UUID> ids = new ArrayList<>();
    for (PlannedMatch m : plan.matches()) {
      if (m.round() <= 2) {
        if (m.home() != null) ids.add(m.home());
        if (m.away() != null) ids.add(m.away());
      }
    }
    return ids;
  }

  @Test
  void seedOrder() {
    assertThat(Bracket.seedOrder(2)).containsExactly(1, 2);
    assertThat(Bracket.seedOrder(4)).containsExactly(1, 4, 2, 3);
    assertThat(Bracket.seedOrder(8)).containsExactly(1, 8, 4, 5, 2, 7, 3, 6);
  }

  @Test
  void twoTeamsIsOneFinal() {
    List<SeededTeam> teams = seeded(2);
    BracketPlan plan = Bracket.plan(teams, new Random(1));
    assertThat(plan.rounds()).isEqualTo(1);
    assertThat(plan.matches()).containsExactly(new PlannedMatch(1, 0, teams.get(0).teamId(), teams.get(1).teamId()));
  }

  @Test
  void sixTeamsTopSeedsGetByes() {
    List<SeededTeam> t = seeded(6);
    BracketPlan plan = Bracket.plan(t, new Random(1));
    assertThat(plan.rounds()).isEqualTo(3);
    assertThat(plan.matches().stream().filter(m -> m.round() == 1).map(PlannedMatch::position)).containsExactlyInAnyOrder(1, 3);
    assertThat(at(plan, 1, 1)).isEqualTo(new PlannedMatch(1, 1, t.get(3).teamId(), t.get(4).teamId()));
    assertThat(at(plan, 1, 3)).isEqualTo(new PlannedMatch(1, 3, t.get(2).teamId(), t.get(5).teamId()));
    assertThat(at(plan, 2, 0)).isEqualTo(new PlannedMatch(2, 0, t.get(0).teamId(), null));
    assertThat(at(plan, 2, 1)).isEqualTo(new PlannedMatch(2, 1, t.get(1).teamId(), null));
    assertThat(at(plan, 3, 0)).isEqualTo(new PlannedMatch(3, 0, null, null));
  }

  @Test
  void matchCountIsTeamsMinusOne() {
    for (int n = 2; n <= 20; n++) assertThat(Bracket.plan(seeded(n), new Random(n)).matches()).as("n=%d", n).hasSize(n - 1);
  }

  @Test
  void everyTeamPlacedOnce() {
    for (int n = 2; n <= 20; n++) {
      List<SeededTeam> teams = unseeded(n);
      List<UUID> placed = placed(Bracket.plan(teams, new Random(n)));
      assertThat(placed).as("n=%d", n).containsExactlyInAnyOrderElementsOf(teams.stream().map(SeededTeam::teamId).toList());
    }
  }

  @Test
  void topTwoSeedsMeetOnlyInFinal() {
    List<SeededTeam> t = seeded(8);
    BracketPlan plan = Bracket.plan(t, new Random(1));
    int seed1 = plan.matches().stream().filter(m -> m.round() == 1 && t.get(0).teamId().equals(m.home())).findFirst().orElseThrow().position();
    int seed2 = plan.matches().stream().filter(m -> m.round() == 1 && t.get(1).teamId().equals(m.home())).findFirst().orElseThrow().position();
    assertThat(seed1).isIn(0, 1);
    assertThat(seed2).isIn(2, 3);
  }

  @Test
  void unseededDrawIsRandomButComplete() {
    List<SeededTeam> teams = unseeded(5);
    BracketPlan a = Bracket.plan(teams, new Random(42));
    BracketPlan b = Bracket.plan(teams, new Random(42));
    assertThat(a).isEqualTo(b);
    assertThat(placed(a)).hasSize(5).doesNotContainNull();
    assertThat(a.matches()).hasSize(4);
  }

  @Test
  void seededFirstThenUnseeded() {
    UUID x = UUID.randomUUID();
    UUID y = UUID.randomUUID();
    List<SeededTeam> teams = List.of(new SeededTeam(UUID.randomUUID(), null), new SeededTeam(y, 2),
        new SeededTeam(UUID.randomUUID(), null), new SeededTeam(x, 1));
    BracketPlan plan = Bracket.plan(teams, new Random(3));
    assertThat(at(plan, 1, 0).home()).isEqualTo(x);
    assertThat(at(plan, 1, 1).home()).isEqualTo(y);
  }

  @Test
  void seedsNeedNotBeContiguous() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    List<SeededTeam> teams = List.of(new SeededTeam(a, 7), new SeededTeam(b, 3),
        new SeededTeam(UUID.randomUUID(), null), new SeededTeam(UUID.randomUUID(), null));
    BracketPlan plan = Bracket.plan(teams, new Random(3));
    assertThat(at(plan, 1, 0).home()).isEqualTo(b);
    assertThat(at(plan, 1, 1).home()).isEqualTo(a);
  }

  @Test
  void rejectsOneOrSixtyFiveTeams() {
    assertThatThrownBy(() -> Bracket.plan(seeded(1), new Random())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Bracket.plan(seeded(65), new Random())).isInstanceOf(IllegalArgumentException.class);
    assertThat(Bracket.plan(seeded(64), new Random()).matches().stream().filter(Objects::nonNull)).hasSize(63);
  }
}
