package com.sportsapp.match;

import com.sportsapp.common.Visibility;
import com.sportsapp.sport.Score;
import com.sportsapp.sport.Sport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MatchDtos {

  private MatchDtos() {}

  /** Blank becomes null, so "  " counts as no label and "" as no venue. */
  static String trimToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  /** Either two team ids (team match) or two labels (pickup). */
  public record CreateMatchRequest(
      UUID homeTeamId, UUID awayTeamId,
      @Size(max = 30) String homeLabel, @Size(max = 30) String awayLabel,
      Instant kickoffAt, @Size(max = 100) String venue, @NotNull Visibility visibility) {
    public CreateMatchRequest {
      homeLabel = trimToNull(homeLabel);
      awayLabel = trimToNull(awayLabel);
      venue = trimToNull(venue);
    }
  }

  /** Null fields stay unchanged; {@code venue: ""} clears the venue. */
  public record PatchMatchRequest(@NotNull Long version, Instant kickoffAt, @Size(max = 100) String venue, Visibility visibility) {}

  /** {@code inGoal} may be left out (false). */
  public record LineupEntry(@NotNull UUID playerId, @NotNull Side side, Boolean inGoal) {
    public LineupEntry {
      inGoal = Boolean.TRUE.equals(inGoal);
    }
  }

  public record SetLineupRequest(@NotNull @Valid List<@NotNull LineupEntry> entries) {}

  /** {@code name}: the team's name, or the pickup label. */
  public record SideView(UUID teamId, String name) {}

  public record LineupView(UUID playerId, String name, Side side, boolean inGoal) {}

  public record EventView(UUID id, EventType type, Side side, UUID playerId, String playerName,
      UUID assistPlayerId, String assistPlayerName, Integer minute) {}

  /** {@code lineup}: HOME first, then by name. {@code events}: by minute (unknown last), then entry order. */
  public record MatchResponse(UUID id, long version, Sport sport, MatchStatus status, Instant kickoffAt, String venue,
      Visibility visibility, UUID competitionId, String competitionName, Integer round, SideView home, SideView away,
      Score score, Side penaltyWinner, List<LineupView> lineup, List<EventView> events, boolean canAdmin) {}

  public record MatchSummary(UUID id, MatchStatus status, Instant kickoffAt, String homeName, String awayName, Score score,
      UUID competitionId, String competitionName, Integer round) {}
}
