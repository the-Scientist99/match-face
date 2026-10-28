package com.sportsapp.match;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MatchRepository extends JpaRepository<Match, UUID> {

  /**
   * Every match write starts here: the row lock makes concurrent writes to one match run one at a time
   * (two lineup saves would otherwise deadlock on the delete/insert gap locks, and a double tap could
   * add two MOTMs).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM Match m WHERE m.id = :id")
  Optional<Match> lockForUpdate(UUID id);

  /**
   * For writes that don't change the match row itself (lineup, events): clients holding the old version
   * then get STALE on their next detail edit.
   */
  @Modifying
  @Query("UPDATE Match m SET m.version = m.version + 1 WHERE m.id = :id")
  void bumpVersion(UUID id);
}
