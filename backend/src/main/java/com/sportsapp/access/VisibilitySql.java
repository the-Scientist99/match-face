package com.sportsapp.access;

/** The one definition of which matches a viewer may see; every read check and stats query uses it. */
public final class VisibilitySql {

  private VisibilitySql() {}

  /**
   * Selects {@code competition.id}s visible to {@code :viewerId}: public ones, ones they admin, and ones a
   * team they play for (their player on its roster) takes part in.
   */
  public static final String VISIBLE_COMPETITION_IDS = """
      SELECT c.id FROM competition c WHERE c.visibility = 'PUBLIC'
        OR EXISTS (SELECT 1 FROM admin_grant g WHERE g.resource_type = 'COMPETITION' AND g.resource_id = c.id AND g.user_id = UUID_TO_BIN(:viewerId))
        OR EXISTS (SELECT 1 FROM competition_team ct JOIN team_player tp ON tp.team_id = ct.team_id JOIN player p ON p.id = tp.player_id
                   WHERE ct.competition_id = c.id AND p.user_id = UUID_TO_BIN(:viewerId))
      """;

  /**
   * Selects {@code sport_match.id}s visible to {@code :viewerId} (bound as a UUID string, or null for an
   * anonymous viewer: {@code UUID_TO_BIN(NULL)} is NULL, so only public rows match). One-off matches by
   * their own visibility, grants and lineup; competition matches by their competition.
   */
  public static final String VISIBLE_MATCH_IDS = """
      SELECT m.id FROM sport_match m
      WHERE m.stage_id IS NULL AND (
        m.visibility = 'PUBLIC'
        OR EXISTS (SELECT 1 FROM admin_grant g WHERE g.resource_type = 'MATCH' AND g.resource_id = m.id AND g.user_id = UUID_TO_BIN(:viewerId))
        OR EXISTS (SELECT 1 FROM match_lineup l JOIN player p ON p.id = l.player_id
                   WHERE l.match_id = m.id AND p.user_id = UUID_TO_BIN(:viewerId)))
      UNION
      SELECT m.id FROM sport_match m JOIN stage s ON s.id = m.stage_id
      WHERE s.competition_id IN (""" + VISIBLE_COMPETITION_IDS + ")";
}
