package com.sportsapp.team;

import com.sportsapp.common.BaseEntity;
import com.sportsapp.sport.Sport;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
public class Team extends BaseEntity {

  private String name;

  @Enumerated(EnumType.STRING)
  private Sport sport;

  protected Team() {}

  public Team(String name, Sport sport) {
    this.name = name;
    this.sport = sport;
  }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public Sport getSport() { return sport; }
}
