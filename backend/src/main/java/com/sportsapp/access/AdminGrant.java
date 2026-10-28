package com.sportsapp.access;

import com.sportsapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "admin_grant")
public class AdminGrant extends BaseEntity {

  /** v1 has one role. */
  static final String OWNER = "OWNER";

  @Column(name = "user_id")
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "resource_type")
  private ResourceType resourceType;

  @Column(name = "resource_id")
  private UUID resourceId;

  private String role;

  protected AdminGrant() {}

  public AdminGrant(UUID userId, ResourceType resourceType, UUID resourceId, String role) {
    this.userId = userId;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.role = role;
  }

  public UUID getUserId() { return userId; }
  public ResourceType getResourceType() { return resourceType; }
  public UUID getResourceId() { return resourceId; }
  public String getRole() { return role; }
}
