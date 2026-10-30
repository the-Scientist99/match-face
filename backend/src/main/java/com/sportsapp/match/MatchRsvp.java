package com.sportsapp.match;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** A roster player's answer to "are you playing?" for a team match. */
@Entity
@Table(name = "match_rsvp")
public class MatchRsvp extends BaseEntity {

  @Column(name = "match_id")
  private UUID matchId;

  @Column(name = "player_id")
  private UUID playerId;

  @Enumerated(EnumType.STRING)
  private Side side;

  private boolean going;

  protected MatchRsvp() {}

  public MatchRsvp(UUID matchId, UUID playerId) {
    this.matchId = matchId;
    this.playerId = playerId;
  }

  public void answer(Side side, boolean going) {
    this.side = side;
    this.going = going;
  }
}
