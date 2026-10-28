package com.sportsapp.competition;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionTeamRepository extends JpaRepository<CompetitionTeam, UUID> {

  List<CompetitionTeam> findByCompetitionId(UUID competitionId);
}
