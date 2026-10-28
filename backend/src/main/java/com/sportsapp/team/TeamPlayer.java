package com.sportsapp.team;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "team_player")
public class TeamPlayer extends BaseEntity {

  @Column(name = "team_id")
  private UUID teamId;

  @Column(name = "player_id")
  private UUID playerId;

  protected TeamPlayer() {}

  public TeamPlayer(UUID teamId, UUID playerId) {
    this.teamId = teamId;
    this.playerId = playerId;
  }

  public UUID getTeamId() { return teamId; }
  public UUID getPlayerId() { return playerId; }
}
