package com.sportsapp.player;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PlayerClaimRepository extends JpaRepository<PlayerClaim, UUID> {

  Optional<PlayerClaim> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM PlayerClaim c WHERE c.tokenHash = :tokenHash")
  Optional<PlayerClaim> lockByTokenHash(String tokenHash);

  @Modifying
  @Query("DELETE FROM PlayerClaim c WHERE c.playerId = :playerId")
  void deleteByPlayerId(UUID playerId);
}
