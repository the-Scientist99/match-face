package com.sportsapp.match;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "match_lineup")
public class MatchLineup extends BaseEntity {

  @Column(name = "match_id")
  private UUID matchId;

  @Column(name = "player_id")
  private UUID playerId;

  @Enumerated(EnumType.STRING)
  private Side side;

  @Column(name = "in_goal")
  private boolean inGoal;

  protected MatchLineup() {}

  public MatchLineup(UUID matchId, UUID playerId, Side side, boolean inGoal) {
    this.matchId = matchId;
    this.playerId = playerId;
    this.side = side;
    this.inGoal = inGoal;
  }

  public UUID getMatchId() { return matchId; }
  public UUID getPlayerId() { return playerId; }
  public Side getSide() { return side; }
  public boolean isInGoal() { return inGoal; }
}
