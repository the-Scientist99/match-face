package com.sportsapp.team;

import com.sportsapp.sport.Sport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class TeamDtos {

  private TeamDtos() {}

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }

  public record CreateTeamRequest(@NotBlank @Size(max = 50) String name) {
    public CreateTeamRequest {
      name = trim(name);
    }
  }

  public record RenameTeamRequest(@NotBlank @Size(max = 50) String name) {
    public RenameTeamRequest {
      name = trim(name);
    }
  }

  public record SetRosterRequest(@NotNull List<@NotNull UUID> playerIds) {}

  public record RosterPlayer(UUID id, String name, boolean claimed) {}

  /** {@code players} sorted by name. */
  public record TeamResponse(UUID id, String name, Sport sport, List<RosterPlayer> players, boolean canAdmin) {}

  /** {@code canAdmin}: the user may manage it (and use it in new matches and competitions). */
  public record TeamSummary(UUID id, String name, boolean canAdmin) {}
}
