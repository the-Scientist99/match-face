package com.sportsapp.match;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRsvpRepository extends JpaRepository<MatchRsvp, UUID> {

  Optional<MatchRsvp> findByMatchIdAndPlayerId(UUID matchId, UUID playerId);
}
