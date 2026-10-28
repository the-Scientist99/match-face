package com.sportsapp.competition;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StageRepository extends JpaRepository<Stage, UUID> {

  /** The database cascades this to the stages' matches, lineups and events. */
  @Modifying
  @Query("DELETE FROM Stage s WHERE s.competitionId = :competitionId")
  void deleteByCompetitionId(UUID competitionId);
}
