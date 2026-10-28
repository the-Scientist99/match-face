package com.sportsapp.match;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {

  List<MatchEvent> findByMatchId(UUID matchId);
}
