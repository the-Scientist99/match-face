package com.sportsapp.stats;

import com.sportsapp.access.VisibilitySql;
import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.match.MatchViews;
import com.sportsapp.match.Side;
import com.sportsapp.stats.StatsDtos.PlayerMatchRow;
import com.sportsapp.stats.StatsDtos.PlayerTotals;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A player's numbers, from FINISHED matches only. Profiles count only matches the viewer can see
 * ({@link VisibilitySql#VISIBLE_MATCH_IDS}); a claim preview counts all of them.
 */
@Service
public class PlayerStatsQuery {

  /** The player's finished matches, as {@code pm(match_id, side, in_goal)}; %s is the visibility filter. */
  private static final String PLAYER_MATCHES = """
      SELECT l.match_id, l.side, l.in_goal FROM match_lineup l JOIN sport_match m ON m.id = l.match_id
      WHERE l.player_id = UUID_TO_BIN(:playerId) AND m.status = 'FINISHED' %s
      """;

  private static final String VISIBLE = "AND m.id IN (" + VisibilitySql.VISIBLE_MATCH_IDS + ")";

  private final JdbcClient jdbc;
  private final MatchViews matchViews;

  public PlayerStatsQuery(JdbcClient jdbc, MatchViews matchViews) {
    this.jdbc = jdbc;
    this.matchViews = matchViews;
  }

  @Transactional(readOnly = true)
  public PlayerTotals totals(UUID playerId, @Nullable UUID viewerId) {
    return totals(playerId, VISIBLE, viewerId);
  }

  /** Every finished match, whoever can see it: for the intended owner of a claim token. */
  @Transactional(readOnly = true)
  public PlayerTotals totalsOfAllMatches(UUID playerId) {
    return totals(playerId, "", null);
  }

  private PlayerTotals totals(UUID playerId, String filter, @Nullable UUID viewerId) {
    String sql = """
        WITH pm AS (%s),
        pe AS (SELECT e.type, e.player_id, e.assist_player_id FROM match_event e JOIN pm ON pm.match_id = e.match_id)
        SELECT
          (SELECT COUNT(*) FROM pm) appearances,
          (SELECT COUNT(*) FROM pe WHERE type = 'GOAL' AND player_id = UUID_TO_BIN(:playerId)) goals,
          (SELECT COUNT(*) FROM pe WHERE type = 'GOAL' AND assist_player_id = UUID_TO_BIN(:playerId)) assists,
          (SELECT COUNT(*) FROM pe WHERE type = 'OWN_GOAL' AND player_id = UUID_TO_BIN(:playerId)) own_goals,
          (SELECT COUNT(*) FROM pe WHERE type = 'YELLOW' AND player_id = UUID_TO_BIN(:playerId)) yellows,
          (SELECT COUNT(*) FROM pe WHERE type = 'RED' AND player_id = UUID_TO_BIN(:playerId)) reds,
          (SELECT COUNT(*) FROM pm WHERE pm.in_goal AND NOT EXISTS (
             SELECT 1 FROM match_event e WHERE e.match_id = pm.match_id
               AND ((e.type = 'GOAL' AND e.side <> pm.side) OR (e.type = 'OWN_GOAL' AND e.side = pm.side)))) clean_sheets,
          (SELECT COUNT(*) FROM pe WHERE type = 'MOTM' AND player_id = UUID_TO_BIN(:playerId)) motm
        """.formatted(PLAYER_MATCHES.formatted(filter));
    var spec = jdbc.sql(sql).param("playerId", playerId.toString());
    if (!filter.isEmpty()) spec = spec.param("viewerId", viewerId == null ? null : viewerId.toString());
    return spec.query((rs, i) -> new PlayerTotals(rs.getInt("appearances"), rs.getInt("goals"), rs.getInt("assists"),
            rs.getInt("own_goals"), rs.getInt("yellows"), rs.getInt("reds"), rs.getInt("clean_sheets"), rs.getInt("motm")))
        .single();
  }

  private record Row(UUID matchId, Side side, int goals, int assists) {}

  /** Newest kickoff first (no kickoff last), then newest created. */
  @Transactional(readOnly = true)
  public List<PlayerMatchRow> matches(UUID playerId, @Nullable UUID viewerId) {
    List<Row> rows = jdbc.sql("""
            SELECT BIN_TO_UUID(pm.match_id) match_id, pm.side,
              (SELECT COUNT(*) FROM match_event e WHERE e.match_id = pm.match_id AND e.type = 'GOAL' AND e.player_id = UUID_TO_BIN(:playerId)) goals,
              (SELECT COUNT(*) FROM match_event e WHERE e.match_id = pm.match_id AND e.type = 'GOAL' AND e.assist_player_id = UUID_TO_BIN(:playerId)) assists
            FROM (%s) pm JOIN sport_match m ON m.id = pm.match_id
            ORDER BY m.kickoff_at IS NULL, m.kickoff_at DESC, m.created_at DESC
            """.formatted(PLAYER_MATCHES.formatted(VISIBLE)))
        .param("playerId", playerId.toString())
        .param("viewerId", viewerId == null ? null : viewerId.toString())
        .query((rs, i) -> new Row(UUID.fromString(rs.getString("match_id")), Side.valueOf(rs.getString("side")),
            rs.getInt("goals"), rs.getInt("assists")))
        .list();
    Map<UUID, MatchSummary> summaries = matchViews.summaries(rows.stream().map(Row::matchId).toList()).stream()
        .collect(Collectors.toMap(MatchSummary::id, Function.identity()));
    return rows.stream().map(r -> new PlayerMatchRow(summaries.get(r.matchId()), r.side(), r.goals(), r.assists())).toList();
  }
}
