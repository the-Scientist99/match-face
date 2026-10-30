package com.sportsapp.competition;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A team waiting to be let into a competition; approving turns it into a {@link CompetitionTeam}. */
@Entity
@Table(name = "competition_entry")
public class CompetitionEntry extends BaseEntity {

  @Column(name = "competition_id")
  private UUID competitionId;

  @Column(name = "team_id")
  private UUID teamId;

  protected CompetitionEntry() {}

  public CompetitionEntry(UUID competitionId, UUID teamId) {
    this.competitionId = competitionId;
    this.teamId = teamId;
  }

  public UUID getCompetitionId() { return competitionId; }
  public UUID getTeamId() { return teamId; }
}
