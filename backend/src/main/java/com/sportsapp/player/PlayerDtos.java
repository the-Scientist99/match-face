package com.sportsapp.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.sportsapp.stats.StatsDtos.PlayerMatchRow;
import com.sportsapp.stats.StatsDtos.PlayerTotals;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlayerDtos {

  private PlayerDtos() {}

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }

  public record CreatePlayerRequest(@NotBlank @Size(max = 50) String name) {
    public CreatePlayerRequest {
      name = trim(name);
    }
  }

  public record RenamePlayerRequest(@NotBlank @Size(max = 50) String name) {
    public RenamePlayerRequest {
      name = trim(name);
    }
  }

  /**
   * {@code totals} and {@code matches} count only finished matches the viewer can see.
   * {@code canInvite}: the viewer created this guest and it is unclaimed.
   */
  public record PlayerProfileResponse(UUID id, String name, boolean claimed, boolean canEdit, boolean canInvite,
      PlayerTotals totals, List<PlayerMatchRow> matches) {}

  public record ClaimInviteResponse(String token, Instant expiresAt) {}

  /** Counts all finished matches: the token holder is the guest's intended owner. */
  public record ClaimPreviewResponse(UUID playerId, String playerName, int appearances, int goals) {}

  /** The claiming user's own player, which now holds the guest's history. */
  public record ClaimResultResponse(UUID playerId) {}

  /** {@code claimed}: the player belongs to a registered user. {@code canEdit}: the viewer may rename it. */
  public record PlayerResponse(UUID id, String name, boolean claimed, boolean canEdit) {}
}
