package com.sportsapp.team;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TeamPlayerRepository extends JpaRepository<TeamPlayer, UUID> {

  List<TeamPlayer> findByTeamId(UUID teamId);

  boolean existsByTeamIdAndPlayerId(UUID teamId, UUID playerId);

  /** A bulk delete runs at once, so re-inserting the same pairs afterwards can't hit the unique index. */
  @Modifying
  @Query("DELETE FROM TeamPlayer tp WHERE tp.teamId = :teamId")
  void deleteByTeamId(UUID teamId);
}
