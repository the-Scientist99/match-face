package com.sportsapp.player;

import com.sportsapp.common.ApiException;
import com.sportsapp.player.PlayerDtos.PlayerResponse;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

  private final PlayerRepository players;
  private final JdbcClient jdbc;

  public PlayerService(PlayerRepository players, JdbcClient jdbc) {
    this.players = players;
    this.jdbc = jdbc;
  }

  /** The player every user gets at registration: owned by and created by that user. */
  @Transactional
  public Player createOwn(UUID userId, String name) {
    return players.save(new Player(name, userId, userId));
  }

  @Transactional
  public Player createGuest(UUID creatorId, String name) {
    return players.save(new Player(name, null, creatorId));
  }

  @Transactional(readOnly = true)
  public Player ownPlayer(UUID userId) {
    return players.findByUserId(userId).orElseThrow(ApiException::notFound);
  }

  @Transactional
  public Player renameOwn(UUID userId, String name) {
    Player p = ownPlayer(userId);
    p.setName(name);
    return p;
  }

  @Transactional
  public Player rename(UUID userId, UUID playerId, String name) {
    Player p = getRequired(playerId);
    if (!canEdit(userId, p)) throw ApiException.forbidden();
    p.setName(name);
    return p;
  }

  @Transactional(readOnly = true)
  public Player getRequired(UUID id) {
    return players.findById(id).orElseThrow(ApiException::notFound);
  }

  /** A guest can be renamed by its creator until it is claimed; a claimed player only by its user. */
  public boolean canEdit(@Nullable UUID userId, Player p) {
    if (userId == null) return false;
    return p.getUserId() == null ? userId.equals(p.getCreatedByUserId()) : userId.equals(p.getUserId());
  }

  /** The lineup and roster picker source: my own player, unclaimed guests I created, players on teams I admin. */
  @Transactional(readOnly = true)
  public Set<UUID> myPlayerIds(UUID userId) {
    return new HashSet<>(jdbc.sql("""
            SELECT BIN_TO_UUID(p.id) FROM player p
            WHERE p.user_id = UUID_TO_BIN(:u) OR (p.created_by_user_id = UUID_TO_BIN(:u) AND p.user_id IS NULL)
            UNION
            SELECT BIN_TO_UUID(tp.player_id) FROM team_player tp
            JOIN admin_grant g ON g.resource_type = 'TEAM' AND g.resource_id = tp.team_id AND g.user_id = UUID_TO_BIN(:u)
            """)
        .param("u", userId.toString())
        .query(UUID.class).list());
  }

  /** My players: own player first, then by name ignoring case. */
  @Transactional(readOnly = true)
  public List<PlayerResponse> mine(UUID userId) {
    return players.findAllById(myPlayerIds(userId)).stream()
        .sorted(Comparator.comparing((Player p) -> !userId.equals(p.getUserId()))
            .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
        .map(p -> response(userId, p))
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<UUID, String> namesById(Collection<UUID> ids) {
    return players.findAllById(ids).stream().collect(Collectors.toMap(Player::getId, Player::getName));
  }

  public PlayerResponse response(@Nullable UUID viewerId, Player p) {
    return new PlayerResponse(p.getId(), p.getName(), p.getUserId() != null, canEdit(viewerId, p));
  }
}
