package com.sportsapp.match;

import com.sportsapp.common.Visibility;
import com.sportsapp.sport.Score;
import com.sportsapp.sport.Sport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
      @NotNull Instant kickoffAt, @Size(max = 100) String venue, @NotNull Visibility visibility) {
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

  /** For OWN_GOAL, {@code side} is the side the scorer plays on. PATCH sends the whole event again. */
  public record EventRequest(@NotNull EventType type, @NotNull Side side, UUID playerId, UUID assistPlayerId,
      @Min(0) @Max(150) Integer minute) {}

  public record VersionRequest(@NotNull Long version) {}

  public record JoinRequest(@NotNull Side side) {}

  /** {@code penaltyWinner} decides a level cup match (M6). */
  public record FinishRequest(@NotNull Long version, Side penaltyWinner) {}

  /** {@code name}: the team's name, or the pickup label. */
  public record SideView(UUID teamId, String name) {}

  public record LineupView(UUID playerId, String name, Side side, boolean inGoal) {}

  public record EventView(UUID id, EventType type, Side side, UUID playerId, String playerName,
      UUID assistPlayerId, String assistPlayerName, Integer minute) {}

  /**
   * {@code lineup}: HOME first, then by name. {@code events}: by minute (unknown last), then entry order.
   * {@code knockout}: a cup match, which can't end level (penalties) and can't be cancelled.
   * {@code mySide}: where the viewer's own player is in the lineup. {@code joinable}: a scheduled public pickup.
   */
  public record MatchResponse(UUID id, long version, Sport sport, MatchStatus status, Instant kickoffAt, String venue,
      Visibility visibility, UUID competitionId, String competitionName, Integer round, SideView home, SideView away,
      Score score, Side penaltyWinner, List<LineupView> lineup, List<EventView> events, boolean canAdmin, boolean knockout,
      Side mySide, boolean joinable) {}

  public record MatchSummary(UUID id, MatchStatus status, Instant kickoffAt, String homeName, String awayName, Score score,
      UUID competitionId, String competitionName, Integer round) {}
}
