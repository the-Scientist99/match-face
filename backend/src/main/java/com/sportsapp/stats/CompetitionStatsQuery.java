package com.sportsapp.stats;

import com.sportsapp.common.Names;
import com.sportsapp.stats.StatsDtos.CardRow;
import com.sportsapp.stats.StatsDtos.CompetitionStatsResponse;
import com.sportsapp.stats.StatsDtos.StatLeader;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Player leaders within one competition, from its FINISHED matches. Callers check read access. */
@Service
public class CompetitionStatsQuery {

  private static final int TOP = 10;

  /** Events of the competition's finished matches, as {@code e}; joined player as {@code p} on %s. */
  private static final String EVENTS = """
      FROM match_event e JOIN sport_match m ON m.id = e.match_id JOIN stage s ON s.id = m.stage_id
      JOIN player p ON p.id = e.%s
      WHERE s.competition_id = UUID_TO_BIN(:c) AND m.status = 'FINISHED'
      """;

  private final JdbcClient jdbc;

  public CompetitionStatsQuery(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public CompetitionStatsResponse stats(UUID competitionId) {
    String c = competitionId.toString();
    List<CardRow> cards = jdbc.sql("SELECT BIN_TO_UUID(p.id) id, p.name, SUM(e.type = 'YELLOW') yellow, SUM(e.type = 'RED') red "
            + EVENTS.formatted("player_id") + " AND e.type IN ('YELLOW', 'RED') GROUP BY p.id, p.name")
        .param("c", c)
        .query((rs, i) -> new CardRow(UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getInt("yellow"), rs.getInt("red")))
        .list().stream()
        .sorted(Comparator.comparingInt((CardRow r) -> -r.red()).thenComparingInt(r -> -r.yellow())
            .thenComparing(CardRow::name, Names.ORDER))
        .limit(TOP)
        .toList();
    return new CompetitionStatsResponse(leaders(c, "player_id"), leaders(c, "assist_player_id"), cards);
  }

  /** GOAL events credited through {@code column}: value desc, then name. */
  private List<StatLeader> leaders(String competitionId, String column) {
    return jdbc.sql("SELECT BIN_TO_UUID(p.id) id, p.name, COUNT(*) value " + EVENTS.formatted(column)
            + " AND e.type = 'GOAL' GROUP BY p.id, p.name")
        .param("c", competitionId)
        .query((rs, i) -> new StatLeader(UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getInt("value")))
        .list().stream()
        .sorted(Comparator.comparingInt((StatLeader l) -> -l.value()).thenComparing(StatLeader::name, Names.ORDER))
        .limit(TOP)
        .toList();
  }
}
