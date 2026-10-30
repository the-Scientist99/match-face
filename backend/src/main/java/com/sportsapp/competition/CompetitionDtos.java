package com.sportsapp.competition;

import com.sportsapp.common.Visibility;
import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.match.Side;
import com.sportsapp.sport.Sport;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CompetitionDtos {

  private CompetitionDtos() {}

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }

  /** {@code doubleRoundRobin} and {@code entriesOpen} may be left out (false). */
  public record CreateCompetitionRequest(@NotBlank @Size(max = 80) String name, @NotNull CompetitionType type,
      @NotNull Visibility visibility, Boolean doubleRoundRobin, Boolean entriesOpen) {
    public CreateCompetitionRequest {
      name = trim(name);
      doubleRoundRobin = Boolean.TRUE.equals(doubleRoundRobin);
      entriesOpen = Boolean.TRUE.equals(entriesOpen);
    }
  }

  /** Null fields stay unchanged; a blank name is ignored. */
  public record PatchCompetitionRequest(@NotNull Long version, @Size(max = 80) String name, Visibility visibility, Boolean doubleRoundRobin,
      Boolean entriesOpen) {
    public PatchCompetitionRequest {
      name = trim(name);
    }
  }

  public record RoundKickoffRequest(@NotNull Instant kickoffAt) {}

  public record AddTeamRequest(@NotNull UUID teamId, @Min(1) @Max(64) Integer seed) {}

  public record EntryRequest(@NotNull UUID teamId) {}

  public record ApproveEntryRequest(@Min(1) @Max(64) Integer seed) {}

  public record CompetitionTeamView(UUID teamId, String name, Integer seed) {}

  public record CompetitionEntryView(UUID teamId, String name) {}

  /**
   * {@code teams}: by seed (unseeded last), then name. {@code completed}: it has matches and every one is
   * FINISHED or CANCELLED. {@code entries}: requests waiting for approval; all of them for the organizer,
   * otherwise those of teams the viewer admins.
   */
  public record CompetitionResponse(UUID id, long version, String name, Sport sport, CompetitionType type, Visibility visibility,
      CompetitionStatus status, boolean doubleRoundRobin, List<CompetitionTeamView> teams, boolean canAdmin, boolean completed,
      String imageUrl, boolean entriesOpen, List<CompetitionEntryView> entries) {}

  public record BracketMatch(MatchSummary match, int round, int position, Side penaltyWinner) {}

  /** {@code matches} by round, then position; {@code winner} is null until the final is FINISHED. */
  public record BracketResponse(int rounds, List<BracketMatch> matches, TeamRef winner) {}

  public record CompetitionSummary(UUID id, String name, CompetitionType type, CompetitionStatus status, String imageUrl) {}
}
