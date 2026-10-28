package com.sportsapp.player;

import com.sportsapp.common.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

  private final PlayerRepository players;

  public PlayerService(PlayerRepository players) {
    this.players = players;
  }

  /** The player every user gets at registration: owned by and created by that user. */
  @Transactional
  public Player createOwn(UUID userId, String name) {
    return players.save(new Player(name, userId, userId));
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
}
