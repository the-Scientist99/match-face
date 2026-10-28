package com.sportsapp.access;

/** The one definition of which matches a viewer may see; every read check and stats query uses it. */
public final class VisibilitySql {

  private VisibilitySql() {}

  /**
   * Selects {@code sport_match.id}s visible to {@code :viewerId} (bound as a UUID string, or null for an
   * anonymous viewer: {@code UUID_TO_BIN(NULL)} is NULL, so only public rows match). M5 adds a UNION for
   * competition matches.
   */
  public static final String VISIBLE_MATCH_IDS = """
      SELECT m.id FROM sport_match m
      WHERE m.stage_id IS NULL AND (
        m.visibility = 'PUBLIC'
        OR EXISTS (SELECT 1 FROM admin_grant g WHERE g.resource_type = 'MATCH' AND g.resource_id = m.id AND g.user_id = UUID_TO_BIN(:viewerId))
        OR EXISTS (SELECT 1 FROM match_lineup l JOIN player p ON p.id = l.player_id
                   WHERE l.match_id = m.id AND p.user_id = UUID_TO_BIN(:viewerId)))
      """;
}
