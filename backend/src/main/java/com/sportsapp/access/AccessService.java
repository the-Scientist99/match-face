package com.sportsapp.access;

import com.sportsapp.common.ApiException;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every read and admin decision. A resource the viewer can't read is a 404 (its existence isn't revealed),
 * one they can read but not admin is a 403. Non-existent ids are never readable.
 */
@Service
public class AccessService {

  private final AdminGrantRepository grants;
  private final JdbcClient jdbc;

  public AccessService(AdminGrantRepository grants, JdbcClient jdbc) {
    this.grants = grants;
    this.jdbc = jdbc;
  }

  @Transactional
  public void grantOwner(UUID userId, ResourceType type, UUID resourceId) {
    grants.save(new AdminGrant(userId, type, resourceId, AdminGrant.OWNER));
  }

  /** TEAM and MATCH: an admin grant. M5 adds "or admin of its competition" for competition matches. */
  @Transactional(readOnly = true)
  public boolean canAdmin(@Nullable UUID userId, ResourceType type, UUID resourceId) {
    return switch (type) {
      case TEAM, MATCH -> userId != null && grants.existsByUserIdAndResourceTypeAndResourceId(userId, type, resourceId);
      case COMPETITION -> throw notYet(type);
    };
  }

  /**
   * TEAM: its admins and users whose player is on the roster (M5 adds readers of a competition it plays in).
   * MATCH: whatever {@link VisibilitySql#VISIBLE_MATCH_IDS} lets the viewer see.
   */
  @Transactional(readOnly = true)
  public boolean canRead(@Nullable UUID viewerId, ResourceType type, UUID resourceId) {
    return switch (type) {
      case TEAM -> canAdmin(viewerId, type, resourceId) || (viewerId != null && jdbc.sql("""
              SELECT EXISTS (SELECT 1 FROM team_player tp JOIN player p ON p.id = tp.player_id
                             WHERE tp.team_id = UUID_TO_BIN(:t) AND p.user_id = UUID_TO_BIN(:u))
              """)
          .param("t", resourceId.toString()).param("u", viewerId.toString())
          .query(Boolean.class).single());
      case MATCH -> jdbc.sql("SELECT EXISTS (SELECT 1 FROM (" + VisibilitySql.VISIBLE_MATCH_IDS + ") v WHERE v.id = UUID_TO_BIN(:id))")
          .param("viewerId", viewerId == null ? null : viewerId.toString()).param("id", resourceId.toString())
          .query(Boolean.class).single();
      case COMPETITION -> throw notYet(type);
    };
  }

  public void requireAdmin(@Nullable UUID userId, ResourceType type, UUID resourceId) {
    requireRead(userId, type, resourceId);
    if (!canAdmin(userId, type, resourceId)) throw ApiException.forbidden();
  }

  public void requireRead(@Nullable UUID viewerId, ResourceType type, UUID resourceId) {
    if (!canRead(viewerId, type, resourceId)) throw ApiException.notFound();
  }

  /** COMPETITION arrives in M5; until then a call with it is a programming error. */
  private static IllegalArgumentException notYet(ResourceType type) {
    return new IllegalArgumentException("Access rules for " + type + " are not implemented yet");
  }
}
