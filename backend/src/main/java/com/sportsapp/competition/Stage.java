package com.sportsapp.competition;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

/** In v1 a league has one ROUND_ROBIN stage and a cup one KNOCKOUT stage. */
@Entity
public class Stage extends BaseEntity {

  @Column(name = "competition_id")
  private UUID competitionId;

  @Enumerated(EnumType.STRING)
  private StageType type;

  private String name;

  @Column(name = "sequence_no")
  private int sequenceNo;

  protected Stage() {}

  public Stage(UUID competitionId, StageType type, String name, int sequenceNo) {
    this.competitionId = competitionId;
    this.type = type;
    this.name = name;
    this.sequenceNo = sequenceNo;
  }

  public UUID getCompetitionId() { return competitionId; }
  public StageType getType() { return type; }
  public String getName() { return name; }
  public int getSequenceNo() { return sequenceNo; }
}
