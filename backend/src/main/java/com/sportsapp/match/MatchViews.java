package com.sportsapp.match;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.ApiException;
import com.sportsapp.common.Names;
import com.sportsapp.common.Visibility;
import com.sportsapp.image.ImageStore;
import com.sportsapp.match.MatchDtos.EventView;
import com.sportsapp.match.MatchDtos.LineupView;
import com.sportsapp.match.MatchDtos.MatchResponse;
import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.match.MatchDtos.SideView;
import com.sportsapp.sport.Score;
import com.sportsapp.sport.ScoreEvent;
import com.sportsapp.sport.Sport;
import com.sportsapp.sport.SportRules;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read models for matches, straight from SQL. Callers check read access first. Reused by M4–M6. */
@Service
public class MatchViews {

  private final JdbcClient jdbc;
  private final AccessService access;

  public MatchViews(JdbcClient jdbc, AccessService access) {
    this.jdbc = jdbc;
    this.access = access;
  }

  private record MatchRow(long version, Sport sport, MatchStatus status, Instant kickoffAt, String venue,
      Visibility visibility, Integer round, Side penaltyWinner, SideView home, SideView away, UUID competitionId,
      String competitionName, boolean knockout, Integer maxPerSide) {}

  @Transactional(readOnly = true)
  public MatchResponse view(UUID matchId, @Nullable UUID viewerId) {
    String id = matchId.toString();
    var row = jdbc.sql("""
            SELECT m.version, m.sport, m.status, m.kickoff_at, m.venue, COALESCE(c.visibility, m.visibility) visibility,
                   m.round, m.penalty_winner_side, BIN_TO_UUID(c.id) competition_id, c.name competition_name,
                   m.bracket_position IS NOT NULL knockout, m.max_per_side,
                   BIN_TO_UUID(m.home_team_id) home_team_id, BIN_TO_UUID(m.away_team_id) away_team_id,
                   COALESCE(ht.name, m.home_label) home_name, COALESCE(at.name, m.away_label) away_name,
                   ht.image home_image, at.image away_image
            FROM sport_match m
            LEFT JOIN team ht ON ht.id = m.home_team_id
            LEFT JOIN team at ON at.id = m.away_team_id
            LEFT JOIN stage s ON s.id = m.stage_id
            LEFT JOIN competition c ON c.id = s.competition_id
            WHERE m.id = UUID_TO_BIN(:id)
            """)
        .param("id", id)
        .query((rs, i) -> new MatchRow(rs.getLong("version"), Sport.valueOf(rs.getString("sport")),
            MatchStatus.valueOf(rs.getString("status")), instant(rs, "kickoff_at"), rs.getString("venue"),
            rs.getString("visibility") == null ? null : Visibility.valueOf(rs.getString("visibility")),
            rs.getObject("round", Integer.class),
            rs.getString("penalty_winner_side") == null ? null : Side.valueOf(rs.getString("penalty_winner_side")),
            new SideView(uuid(rs, "home_team_id"), rs.getString("home_name"), ImageStore.url(rs.getString("home_image"))),
            new SideView(uuid(rs, "away_team_id"), rs.getString("away_name"), ImageStore.url(rs.getString("away_image"))),
            uuid(rs, "competition_id"), rs.getString("competition_name"), rs.getBoolean("knockout"),
            rs.getObject("max_per_side", Integer.class)))
        .optional().orElseThrow(ApiException::notFound);

    List<LineupView> lineup = jdbc.sql("""
            SELECT BIN_TO_UUID(l.player_id) player_id, p.name, l.side, l.in_goal
            FROM match_lineup l JOIN player p ON p.id = l.player_id
            WHERE l.match_id = UUID_TO_BIN(:id)
            """)
        .param("id", id)
        .query((rs, i) -> new LineupView(uuid(rs, "player_id"), rs.getString("name"), Side.valueOf(rs.getString("side")), rs.getBoolean("in_goal")))
        .list().stream()
        .sorted(Comparator.comparing(LineupView::side).thenComparing(LineupView::name, Names.ORDER))
        .toList();

    List<EventView> events = jdbc.sql("""
            SELECT BIN_TO_UUID(e.id) id, e.type, e.side, e.minute,
                   BIN_TO_UUID(e.player_id) player_id, p.name player_name,
                   BIN_TO_UUID(e.assist_player_id) assist_player_id, a.name assist_name
            FROM match_event e
            LEFT JOIN player p ON p.id = e.player_id
            LEFT JOIN player a ON a.id = e.assist_player_id
            WHERE e.match_id = UUID_TO_BIN(:id)
            ORDER BY e.minute IS NULL, e.minute, e.created_at, e.id
            """)
        .param("id", id)
        .query((rs, i) -> new EventView(uuid(rs, "id"), EventType.valueOf(rs.getString("type")), Side.valueOf(rs.getString("side")),
            uuid(rs, "player_id"), rs.getString("player_name"), uuid(rs, "assist_player_id"), rs.getString("assist_name"),
            rs.getObject("minute", Integer.class)))
        .list();

    Score score = SportRules.of(row.sport()).score(events.stream().map(e -> new ScoreEvent(e.type(), e.side())).toList());
    return new MatchResponse(matchId, row.version(), row.sport(), row.status(), row.kickoffAt(), row.venue(), row.visibility(),
        row.competitionId(), row.competitionName(), row.round(), row.home(), row.away(), score, row.penaltyWinner(), lineup, events,
        access.canAdmin(viewerId, ResourceType.MATCH, matchId), row.knockout(), mySide(matchId, viewerId),
        viewerId != null && row.competitionId() == null && row.home().teamId() == null
            && row.visibility() == Visibility.PUBLIC && row.status() == MatchStatus.SCHEDULED, row.maxPerSide());
  }

  /** The side the viewer's own player is on in this match, or null. */
  private Side mySide(UUID matchId, @Nullable UUID viewerId) {
    if (viewerId == null) return null;
    return jdbc.sql("""
            SELECT l.side FROM match_lineup l JOIN player p ON p.id = l.player_id
            WHERE l.match_id = UUID_TO_BIN(:m) AND p.user_id = UUID_TO_BIN(:u)
            """)
        .param("m", matchId.toString()).param("u", viewerId.toString())
        .query(String.class).optional().map(Side::valueOf).orElse(null);
  }

  /** In the order of {@code matchIds}; ids that don't exist are skipped. */
  @Transactional(readOnly = true)
  public List<MatchSummary> summaries(List<UUID> matchIds) {
    if (matchIds.isEmpty()) return List.of();
    Map<UUID, MatchSummary> byId = jdbc.sql("""
            SELECT BIN_TO_UUID(m.id) id, m.status, m.kickoff_at, m.round,
                   BIN_TO_UUID(MAX(c.id)) competition_id, MAX(c.name) competition_name,
                   COALESCE(ht.name, m.home_label) home_name, COALESCE(at.name, m.away_label) away_name,
                   ht.image home_image, at.image away_image, %s home_goals, %s away_goals,
                   GREATEST(m.max_per_side - (SELECT COUNT(*) FROM match_lineup l WHERE l.match_id = m.id AND l.side = 'HOME'), 0)
                   + GREATEST(m.max_per_side - (SELECT COUNT(*) FROM match_lineup l WHERE l.match_id = m.id AND l.side = 'AWAY'), 0)
                     spots_left
            FROM sport_match m
            LEFT JOIN team ht ON ht.id = m.home_team_id
            LEFT JOIN team at ON at.id = m.away_team_id
            LEFT JOIN stage s ON s.id = m.stage_id
            LEFT JOIN competition c ON c.id = s.competition_id
            LEFT JOIN match_event e ON e.match_id = m.id
            WHERE m.id IN (:ids)
            GROUP BY m.id
            """.formatted(ScoreSql.HOME_GOALS, ScoreSql.AWAY_GOALS))
        .param("ids", matchIds.stream().map(MatchViews::bytes).toList())
        .query((rs, i) -> new MatchSummary(uuid(rs, "id"), MatchStatus.valueOf(rs.getString("status")), instant(rs, "kickoff_at"),
            rs.getString("home_name"), rs.getString("away_name"), new Score(rs.getInt("home_goals"), rs.getInt("away_goals")),
            uuid(rs, "competition_id"), rs.getString("competition_name"), rs.getObject("round", Integer.class),
            ImageStore.url(rs.getString("home_image")), ImageStore.url(rs.getString("away_image")),
            rs.getObject("spots_left", Integer.class)))
        .list().stream()
        .collect(Collectors.toMap(MatchSummary::id, Function.identity()));
    return matchIds.stream().map(byId::get).filter(s -> s != null).toList();
  }

  /** Hibernate writes DATETIME in UTC (hibernate.jdbc.time_zone), so read it back as UTC, not the JVM's zone. */
  static Instant instant(ResultSet rs, String column) throws SQLException {
    LocalDateTime t = rs.getObject(column, LocalDateTime.class);
    return t == null ? null : t.toInstant(ZoneOffset.UTC);
  }

  static UUID uuid(ResultSet rs, String column) throws SQLException {
    String s = rs.getString(column);
    return s == null ? null : UUID.fromString(s);
  }

  /** BINARY(16) in the standard byte order (what UUID_TO_BIN gives without the swap flag), for IN lists. */
  static byte[] bytes(UUID u) {
    return ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();
  }
}
