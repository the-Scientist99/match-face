package com.sportsapp.player;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An invite to claim a guest. No "used" flag: a claim deletes the guest, which cascades to its claims. */
@Entity
@Table(name = "player_claim")
public class PlayerClaim extends BaseEntity {

  @Column(name = "player_id")
  private UUID playerId;

  @Column(name = "token_hash")
  private String tokenHash;

  @Column(name = "expires_at")
  private Instant expiresAt;

  protected PlayerClaim() {}

  public PlayerClaim(UUID playerId, String tokenHash, Instant expiresAt) {
    this.playerId = playerId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public UUID getPlayerId() { return playerId; }
  public String getTokenHash() { return tokenHash; }
  public Instant getExpiresAt() { return expiresAt; }
}
