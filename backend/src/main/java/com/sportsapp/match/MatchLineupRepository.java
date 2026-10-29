package com.sportsapp.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MatchLineupRepository extends JpaRepository<MatchLineup, UUID> {

  List<MatchLineup> findByMatchId(UUID matchId);

  Optional<MatchLineup> findByMatchIdAndPlayerId(UUID matchId, UUID playerId);

  /** A bulk delete runs at once, so re-inserting the same players afterwards can't hit the unique index. */
  @Modifying
  @Query("DELETE FROM MatchLineup l WHERE l.matchId = :matchId")
  void deleteByMatchId(UUID matchId);

  @Modifying
  @Query("DELETE FROM MatchLineup l WHERE l.matchId = :matchId AND l.side = :side")
  void deleteByMatchIdAndSide(UUID matchId, Side side);
}
