package com.sportsapp.competition;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CompetitionRepository extends JpaRepository<Competition, UUID> {

  /** Every competition write starts here, before any other read (the snapshot is fixed by the first plain read). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Competition c WHERE c.id = :id")
  Optional<Competition> lockForUpdate(UUID id);
}
