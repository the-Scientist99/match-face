package com.sportsapp.competition;

import com.sportsapp.common.Names;
import com.sportsapp.sport.SportRules;
import com.sportsapp.sport.Tiebreaker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A league table from finished results, ordered by the sport's tiebreakers. Pure: no database. */
public final class Standings {

  private Standings() {}

  /** Mutable tally while accumulating. */
  private static final class Tally {
    final TeamRef team;
    int played, won, drawn, lost, goalsFor, goalsAgainst, points;

    Tally(TeamRef team) {
      this.team = team;
    }

    void add(int scored, int conceded, SportRules rules) {
      played++;
      goalsFor += scored;
      goalsAgainst += conceded;
      points += rules.points(scored, conceded);
      if (scored > conceded) won++;
      else if (scored == conceded) drawn++;
      else lost++;
    }

    int goalDifference() {
      return goalsFor - goalsAgainst;
    }
  }

  /**
   * Every team gets a row, played or not; positions run 1..n with no ties. Criteria before HEAD_TO_HEAD
   * sort the table; teams still level on all of them are ordered by points from their games against each
   * other, then by NAME.
   */
  public static List<StandingRow> compute(List<TeamRef> teams, List<Result> results, SportRules rules) {
    Map<UUID, Tally> byId = new LinkedHashMap<>();
    teams.forEach(t -> byId.put(t.id(), new Tally(t)));
    for (Result r : results) {
      byId.get(r.home()).add(r.homeGoals(), r.awayGoals(), rules);
      byId.get(r.away()).add(r.awayGoals(), r.homeGoals(), rules);
    }

    Comparator<Tally> before = (a, b) -> 0;
    for (Tiebreaker t : rules.tiebreakers()) {
      if (t == Tiebreaker.HEAD_TO_HEAD) break;
      before = before.thenComparing(criterion(t));
    }
    List<Tally> sorted = new ArrayList<>(byId.values());
    sorted.sort(before);

    List<Tally> ordered = new ArrayList<>();
    for (int i = 0; i < sorted.size(); ) {
      int j = i + 1;
      while (j < sorted.size() && before.compare(sorted.get(i), sorted.get(j)) == 0) j++;
      List<Tally> group = new ArrayList<>(sorted.subList(i, j));
      if (group.size() > 1) {
        Map<UUID, Integer> h2h = headToHeadPoints(group, results, rules);
        group.sort(Comparator.<Tally>comparingInt(t -> -h2h.get(t.team.id())).thenComparing(criterion(Tiebreaker.NAME)));
      }
      ordered.addAll(group);
      i = j;
    }

    List<StandingRow> rows = new ArrayList<>();
    for (int p = 0; p < ordered.size(); p++) {
      Tally t = ordered.get(p);
      rows.add(new StandingRow(p + 1, t.team.id(), t.team.name(), t.played, t.won, t.drawn, t.lost,
          t.goalsFor, t.goalsAgainst, t.goalDifference(), t.points));
    }
    return rows;
  }

  /** Better first. HEAD_TO_HEAD is handled per group, not here. */
  private static Comparator<Tally> criterion(Tiebreaker t) {
    return switch (t) {
      case POINTS -> Comparator.comparingInt((Tally x) -> -x.points);
      case GOAL_DIFFERENCE -> Comparator.comparingInt((Tally x) -> -x.goalDifference());
      case GOALS_FOR -> Comparator.comparingInt((Tally x) -> -x.goalsFor);
      case NAME -> Comparator.comparing((Tally x) -> x.team.name(), Names.ORDER);
      case HEAD_TO_HEAD -> throw new IllegalArgumentException("HEAD_TO_HEAD needs the group");
    };
  }

  private static Map<UUID, Integer> headToHeadPoints(List<Tally> group, List<Result> results, SportRules rules) {
    Set<UUID> members = new HashSet<>();
    group.forEach(t -> members.add(t.team.id()));
    Map<UUID, Integer> points = new LinkedHashMap<>();
    members.forEach(id -> points.put(id, 0));
    for (Result r : results) {
      if (!members.contains(r.home()) || !members.contains(r.away())) continue;
      points.merge(r.home(), rules.points(r.homeGoals(), r.awayGoals()), Integer::sum);
      points.merge(r.away(), rules.points(r.awayGoals(), r.homeGoals()), Integer::sum);
    }
    return points;
  }
}
