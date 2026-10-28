package com.sportsapp.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

  /** {@code claimed}: the player belongs to a registered user. {@code canEdit}: the viewer may rename it. */
  public record PlayerResponse(UUID id, String name, boolean claimed, boolean canEdit) {}
}
