package com.sportsapp.access;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminGrantRepository extends JpaRepository<AdminGrant, UUID> {

  boolean existsByUserIdAndResourceTypeAndResourceId(UUID userId, ResourceType resourceType, UUID resourceId);
}
