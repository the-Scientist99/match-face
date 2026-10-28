package com.sportsapp.account;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "token_hash")
  private String tokenHash;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected RefreshToken() {}

  public RefreshToken(UUID userId, String tokenHash, Instant expiresAt, Instant revokedAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
  }

  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getTokenHash() { return tokenHash; }
  public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
