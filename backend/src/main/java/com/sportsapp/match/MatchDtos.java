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

  /** Either two team ids (team match) or two labels (pickup). {@code maxPerSide}: pickups only, null for no limit. */
  public record CreateMatchRequest(
      UUID homeTeamId, UUID awayTeamId,
      @Size(max = 30) String homeLabel, @Size(max = 30) String awayLabel,
      @NotNull Instant kickoffAt, @Size(max = 100) String venue, @NotNull Visibility visibility,
      @Min(1) @Max(30) Integer maxPerSide) {
    public CreateMatchRequest {
      homeLabel = trimToNull(homeLabel);
      awayLabel = trimToNull(awayLabel);
      venue = trimToNull(venue);
    }
  }

  /** Null fields stay unchanged; {@code venue: ""} clears the venue and {@code maxPerSide: 0} the limit. */
  public record PatchMatchRequest(@NotNull Long version, Instant kickoffAt, @Size(max = 100) String venue, Visibility visibility,
      @Min(0) @Max(30) Integer maxPerSide) {}

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

  public record RsvpRequest(@NotNull Boolean going) {}

  public record RsvpView(UUID playerId, String name, Side side, boolean going) {}

  /** {@code penaltyWinner} decides a level cup match (M6). */
  public record FinishRequest(@NotNull Long version, Side penaltyWinner) {}

  /** {@code name}: the team's name, or the pickup label. {@code imageUrl}: the team's logo (none for pickup sides). */
  public record SideView(UUID teamId, String name, String imageUrl) {}

  public record LineupView(UUID playerId, String name, Side side, boolean inGoal) {}

  public record EventView(UUID id, EventType type, Side side, UUID playerId, String playerName,
      UUID assistPlayerId, String assistPlayerName, Integer minute) {}

  /**
   * {@code lineup}: HOME first, then by name. {@code events}: by minute (unknown last), then entry order.
   * {@code knockout}: a cup match, which can't end level (penalties) and can't be cancelled.
   * {@code mySide}: where the viewer's own player is in the lineup. {@code joinable}: a scheduled public pickup.
   * {@code maxPerSide}: a pickup's player limit per side, null for none. {@code rsvps}: roster players' answers,
   * HOME first, then by name. {@code canRsvp}: the viewer is on a roster of this scheduled team match;
   * {@code myRsvp} is their answer (null: none yet).
   */
  public record MatchResponse(UUID id, long version, Sport sport, MatchStatus status, Instant kickoffAt, String venue,
      Visibility visibility, UUID competitionId, String competitionName, Integer round, SideView home, SideView away,
      Score score, Side penaltyWinner, List<LineupView> lineup, List<EventView> events, boolean canAdmin, boolean knockout,
      Side mySide, boolean joinable, Integer maxPerSide, List<RsvpView> rsvps, boolean canRsvp, Boolean myRsvp) {}

  /** {@code spotsLeft}: free places on both sides of a capped pickup, null if it has no limit. */
  public record MatchSummary(UUID id, MatchStatus status, Instant kickoffAt, String homeName, String awayName, Score score,
      UUID competitionId, String competitionName, Integer round,
      String homeImageUrl, String awayImageUrl, Integer spotsLeft) {}
}
