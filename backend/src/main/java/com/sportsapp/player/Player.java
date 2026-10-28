package com.sportsapp.player;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.util.UUID;

@Entity
public class Player extends BaseEntity {

  private String name;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "created_by_user_id")
  private UUID createdByUserId;

  protected Player() {}

  public Player(String name, UUID userId, UUID createdByUserId) {
    this.name = name;
    this.userId = userId;
    this.createdByUserId = createdByUserId;
  }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public UUID getCreatedByUserId() { return createdByUserId; }
  public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
}
