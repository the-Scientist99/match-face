package com.sportsapp.competition;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsapp.sport.Sport;
import com.sportsapp.sport.SportRules;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StandingsTest {

  private final Map<String, UUID> ids = new HashMap<>();

  private UUID id(String name) {
    return ids.computeIfAbsent(name, n -> UUID.randomUUID());
  }

  private List<TeamRef> teams(String... names) {
    return Arrays.stream(names).map(n -> new TeamRef(id(n), n)).toList();
  }

  private Result r(String home, int hg, int ag, String away) {
    return new Result(id(home), id(away), hg, ag);
  }

  private List<StandingRow> compute(List<TeamRef> teams, Result... results) {
    return Standings.compute(teams, List.of(results), SportRules.of(Sport.FOOTBALL));
  }

  private static List<String> names(List<StandingRow> rows) {
    return rows.stream().map(StandingRow::name).toList();
  }

  private static StandingRow row(List<StandingRow> rows, String name) {
    return rows.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
  }

  @Test
  void noResultsSortedByName() {
    List<StandingRow> rows = compute(teams("b", "A", "C"));
    assertThat(names(rows)).containsExactly("A", "b", "C");
    assertThat(rows).extracting(StandingRow::position).containsExactly(1, 2, 3);
    assertThat(rows).allSatisfy(r -> assertThat(r.played() + r.points() + r.goalsFor()).isZero());
  }

  @Test
  void countsAndPoints() {
    List<StandingRow> rows = compute(teams("A", "B", "C"), r("A", 2, 0, "B"), r("A", 1, 1, "C"));
    assertThat(row(rows, "A")).isEqualTo(new StandingRow(1, id("A"), "A", 2, 1, 1, 0, 3, 1, 2, 4));
    StandingRow b = row(rows, "B");
    assertThat(b.played()).isEqualTo(1);
    assertThat(b.lost()).isEqualTo(1);
    assertThat(b.points()).isZero();
    assertThat(b.position()).isEqualTo(3);
  }

  @Test
  void goalDifferenceBeforeGoalsFor() {
    // A: 3-0 win → GD +3, GF 3. B: 5-4 win → GD +1, GF 5.
    List<StandingRow> rows = compute(teams("A", "B", "X", "Y"), r("A", 3, 0, "X"), r("B", 5, 4, "Y"));
    assertThat(names(rows).subList(0, 2)).containsExactly("A", "B");
  }

  @Test
  void goalsForBreaksEqualGd() {
    List<StandingRow> rows = compute(teams("A", "B", "X", "Y"), r("B", 2, 0, "X"), r("A", 4, 2, "Y"));
    assertThat(names(rows).subList(0, 2)).containsExactly("A", "B");
  }

  @Test
  void headToHeadBreaksFullTie() {
    // all 3 pts, GD 0; C has GF 3 → first; A and B tied on GF 2; B beat A
    List<StandingRow> rows = compute(teams("A", "B", "C"), r("B", 1, 0, "A"), r("A", 2, 1, "C"), r("C", 2, 1, "B"));
    assertThat(names(rows)).containsExactly("C", "B", "A");
  }

  @Test
  void threeWayHeadToHeadTieFallsBackToName() {
    List<StandingRow> rows = compute(teams("C", "B", "A"), r("A", 1, 0, "B"), r("B", 1, 0, "C"), r("C", 1, 0, "A"));
    assertThat(names(rows)).containsExactly("A", "B", "C");
  }

  @Test
  void nameUsesTheMontenegrinAlphabet() {
    assertThat(names(compute(teams("Čelik", "Cvijet", "Dečić")))).containsExactly("Cvijet", "Čelik", "Dečić");
  }
}
