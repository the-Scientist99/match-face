package com.sportsapp.competition;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "competition_team")
public class CompetitionTeam extends BaseEntity {

  @Column(name = "competition_id")
  private UUID competitionId;

  @Column(name = "team_id")
  private UUID teamId;

  private Integer seed;

  protected CompetitionTeam() {}

  public CompetitionTeam(UUID competitionId, UUID teamId, Integer seed) {
    this.competitionId = competitionId;
    this.teamId = teamId;
    this.seed = seed;
  }

  public UUID getCompetitionId() { return competitionId; }
  public UUID getTeamId() { return teamId; }
  public Integer getSeed() { return seed; }
}
