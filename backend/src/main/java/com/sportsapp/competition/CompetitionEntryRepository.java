package com.sportsapp.competition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionEntryRepository extends JpaRepository<CompetitionEntry, UUID> {

  List<CompetitionEntry> findByCompetitionId(UUID competitionId);

  Optional<CompetitionEntry> findByCompetitionIdAndTeamId(UUID competitionId, UUID teamId);

  void deleteByCompetitionId(UUID competitionId);

  void deleteByCompetitionIdAndTeamId(UUID competitionId, UUID teamId);
}
