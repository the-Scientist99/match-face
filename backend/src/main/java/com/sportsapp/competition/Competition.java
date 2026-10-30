package com.sportsapp.competition;

import com.sportsapp.common.BaseEntity;
import com.sportsapp.common.Visibility;
import com.sportsapp.sport.Sport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;

@Entity
public class Competition extends BaseEntity {

  @Version
  private long version;

  private String name;

  /** Read-only here: only {@code ImageService} writes it. */
  @Column(insertable = false, updatable = false)
  private String image;

  @Enumerated(EnumType.STRING)
  private Sport sport;

  @Enumerated(EnumType.STRING)
  private CompetitionType type;

  @Enumerated(EnumType.STRING)
  private Visibility visibility;

  @Enumerated(EnumType.STRING)
  private CompetitionStatus status;

  @Column(name = "double_round_robin")
  private boolean doubleRoundRobin;

  @Column(name = "entries_open")
  private boolean entriesOpen;

  protected Competition() {}

  public Competition(String name, Sport sport, CompetitionType type, Visibility visibility, boolean doubleRoundRobin) {
    this.name = name;
    this.sport = sport;
    this.type = type;
    this.visibility = visibility;
    this.status = CompetitionStatus.DRAFT;
    this.doubleRoundRobin = doubleRoundRobin;
  }

  public long getVersion() { return version; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getImage() { return image; }
  public Sport getSport() { return sport; }
  public CompetitionType getType() { return type; }
  public Visibility getVisibility() { return visibility; }
  public void setVisibility(Visibility visibility) { this.visibility = visibility; }
  public CompetitionStatus getStatus() { return status; }
  public void setStatus(CompetitionStatus status) { this.status = status; }
  public boolean isDoubleRoundRobin() { return doubleRoundRobin; }
  public void setDoubleRoundRobin(boolean doubleRoundRobin) { this.doubleRoundRobin = doubleRoundRobin; }
  public boolean isEntriesOpen() { return entriesOpen; }
  public void setEntriesOpen(boolean entriesOpen) { this.entriesOpen = entriesOpen; }
}
