package com.sportsapp.match;

import com.sportsapp.common.BaseEntity;
import com.sportsapp.common.Visibility;
import com.sportsapp.sport.Sport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sport_match")
public class Match extends BaseEntity {

  @Version
  private long version;

  @Enumerated(EnumType.STRING)
  private Sport sport;

  /** Null for a one-off match. */
  @Column(name = "stage_id")
  private UUID stageId;

  private Integer round;

  @Column(name = "bracket_position")
  private Integer bracketPosition;

  @Column(name = "home_team_id")
  private UUID homeTeamId;

  @Column(name = "away_team_id")
  private UUID awayTeamId;

  @Column(name = "home_label")
  private String homeLabel;

  @Column(name = "away_label")
  private String awayLabel;

  @Column(name = "kickoff_at")
  private Instant kickoffAt;

  private String venue;

  @Enumerated(EnumType.STRING)
  private MatchStatus status;

  /** Set only for one-off matches; competition matches use the competition's. */
  @Enumerated(EnumType.STRING)
  private Visibility visibility;

  @Enumerated(EnumType.STRING)
  @Column(name = "penalty_winner_side")
  private Side penaltyWinnerSide;

  @Column(name = "next_match_id")
  private UUID nextMatchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "next_slot")
  private Side nextSlot;

  protected Match() {}

  public Match(Sport sport, MatchStatus status) {
    this.sport = sport;
    this.status = status;
  }

  public long getVersion() { return version; }
  public Sport getSport() { return sport; }
  public UUID getStageId() { return stageId; }
  public void setStageId(UUID stageId) { this.stageId = stageId; }
  public Integer getRound() { return round; }
  public void setRound(Integer round) { this.round = round; }
  public Integer getBracketPosition() { return bracketPosition; }
  public void setBracketPosition(Integer bracketPosition) { this.bracketPosition = bracketPosition; }
  public UUID getHomeTeamId() { return homeTeamId; }
  public void setHomeTeamId(UUID homeTeamId) { this.homeTeamId = homeTeamId; }
  public UUID getAwayTeamId() { return awayTeamId; }
  public void setAwayTeamId(UUID awayTeamId) { this.awayTeamId = awayTeamId; }
  public String getHomeLabel() { return homeLabel; }
  public void setHomeLabel(String homeLabel) { this.homeLabel = homeLabel; }
  public String getAwayLabel() { return awayLabel; }
  public void setAwayLabel(String awayLabel) { this.awayLabel = awayLabel; }
  public Instant getKickoffAt() { return kickoffAt; }
  public void setKickoffAt(Instant kickoffAt) { this.kickoffAt = kickoffAt; }
  public String getVenue() { return venue; }
  public void setVenue(String venue) { this.venue = venue; }
  public MatchStatus getStatus() { return status; }
  public void setStatus(MatchStatus status) { this.status = status; }
  public Visibility getVisibility() { return visibility; }
  public void setVisibility(Visibility visibility) { this.visibility = visibility; }
  public Side getPenaltyWinnerSide() { return penaltyWinnerSide; }
  public void setPenaltyWinnerSide(Side penaltyWinnerSide) { this.penaltyWinnerSide = penaltyWinnerSide; }
  public UUID getNextMatchId() { return nextMatchId; }
  public void setNextMatchId(UUID nextMatchId) { this.nextMatchId = nextMatchId; }
  public Side getNextSlot() { return nextSlot; }
  public void setNextSlot(Side nextSlot) { this.nextSlot = nextSlot; }
}
