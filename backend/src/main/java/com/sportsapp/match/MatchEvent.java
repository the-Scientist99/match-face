package com.sportsapp.match;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "match_event")
public class MatchEvent extends BaseEntity {

  @Column(name = "match_id")
  private UUID matchId;

  @Enumerated(EnumType.STRING)
  private EventType type;

  /** For OWN_GOAL, the side the scorer plays on. */
  @Enumerated(EnumType.STRING)
  private Side side;

  @Column(name = "player_id")
  private UUID playerId;

  @Column(name = "assist_player_id")
  private UUID assistPlayerId;

  private Integer minute;

  protected MatchEvent() {}

  public MatchEvent(UUID matchId, EventType type, Side side, UUID playerId, UUID assistPlayerId, Integer minute) {
    this.matchId = matchId;
    this.type = type;
    this.side = side;
    this.playerId = playerId;
    this.assistPlayerId = assistPlayerId;
    this.minute = minute;
  }

  public UUID getMatchId() { return matchId; }
  public EventType getType() { return type; }
  public void setType(EventType type) { this.type = type; }
  public Side getSide() { return side; }
  public void setSide(Side side) { this.side = side; }
  public UUID getPlayerId() { return playerId; }
  public void setPlayerId(UUID playerId) { this.playerId = playerId; }
  public UUID getAssistPlayerId() { return assistPlayerId; }
  public void setAssistPlayerId(UUID assistPlayerId) { this.assistPlayerId = assistPlayerId; }
  public Integer getMinute() { return minute; }
  public void setMinute(Integer minute) { this.minute = minute; }
}
