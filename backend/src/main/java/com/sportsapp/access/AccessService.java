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

  /** An admin grant on the resource; for a competition match, a grant on its competition also counts. */
  @Transactional(readOnly = true)
  public boolean canAdmin(@Nullable UUID userId, ResourceType type, UUID resourceId) {
    if (userId == null) return false;
    return switch (type) {
      case TEAM, COMPETITION -> grants.existsByUserIdAndResourceTypeAndResourceId(userId, type, resourceId);
      case MATCH -> grants.existsByUserIdAndResourceTypeAndResourceId(userId, type, resourceId) || jdbc.sql("""
              SELECT EXISTS (SELECT 1 FROM sport_match m JOIN stage s ON s.id = m.stage_id
                             JOIN admin_grant g ON g.resource_type = 'COMPETITION' AND g.resource_id = s.competition_id
                             WHERE m.id = UUID_TO_BIN(:m) AND g.user_id = UUID_TO_BIN(:u))
              """)
          .param("m", resourceId.toString()).param("u", userId.toString()).query(Boolean.class).single();
    };
  }

  /**
   * TEAM: its admins, users whose player is on the roster, and readers of a competition it plays in.
   * MATCH and COMPETITION: whatever {@link VisibilitySql} lets the viewer see.
   */
  @Transactional(readOnly = true)
  public boolean canRead(@Nullable UUID viewerId, ResourceType type, UUID resourceId) {
    return switch (type) {
      case TEAM -> canAdmin(viewerId, type, resourceId) || jdbc.sql("""
              SELECT EXISTS (SELECT 1 FROM team_player tp JOIN player p ON p.id = tp.player_id
                             WHERE tp.team_id = UUID_TO_BIN(:t) AND p.user_id = UUID_TO_BIN(:viewerId))
                  OR EXISTS (SELECT 1 FROM competition_team ct
                             WHERE ct.team_id = UUID_TO_BIN(:t) AND ct.competition_id IN (""" + VisibilitySql.VISIBLE_COMPETITION_IDS + "))")
          .param("t", resourceId.toString()).param("viewerId", viewerId == null ? null : viewerId.toString())
          .query(Boolean.class).single();
      case MATCH -> jdbc.sql("SELECT EXISTS (SELECT 1 FROM (" + VisibilitySql.VISIBLE_MATCH_IDS + ") v WHERE v.id = UUID_TO_BIN(:id))")
          .param("viewerId", viewerId == null ? null : viewerId.toString()).param("id", resourceId.toString())
          .query(Boolean.class).single();
      case COMPETITION -> jdbc.sql("SELECT EXISTS (SELECT 1 FROM (" + VisibilitySql.VISIBLE_COMPETITION_IDS + ") v WHERE v.id = UUID_TO_BIN(:id))")
          .param("viewerId", viewerId == null ? null : viewerId.toString()).param("id", resourceId.toString())
          .query(Boolean.class).single();
    };
  }

  public void requireAdmin(@Nullable UUID userId, ResourceType type, UUID resourceId) {
    requireRead(userId, type, resourceId);
    if (!canAdmin(userId, type, resourceId)) throw ApiException.forbidden();
  }

  public void requireRead(@Nullable UUID viewerId, ResourceType type, UUID resourceId) {
    if (!canRead(viewerId, type, resourceId)) throw ApiException.notFound();
  }
}
