package com.sportsapp.image;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.ApiException;
import com.sportsapp.player.PlayerService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Sets or removes the image of a player, team or competition. Plain SQL on purpose: an image isn't a detail
 * edit, so it doesn't bump a competition's version and send other open tabs STALE.
 */
@Service
public class ImageService {

  /** The table is picked from this fixed list, never built from input. */
  public enum Owner {
    PLAYER("player"), TEAM("team"), COMPETITION("competition");

    private final String table;

    Owner(String table) { this.table = table; }
  }

  private final ImageStore store;
  private final AccessService access;
  private final PlayerService players;
  private final JdbcClient jdbc;

  public ImageService(ImageStore store, AccessService access, PlayerService players, JdbcClient jdbc) {
    this.store = store;
    this.access = access;
    this.players = players;
    this.jdbc = jdbc;
  }

  /** {@code file} null removes the image. Returns the new image's file name, or null. */
  @Transactional
  public @Nullable String replace(Owner owner, UUID userId, UUID id, @Nullable MultipartFile file) {
    // lock first, then read: the old file name is the one the last writer committed
    // a list, not optional(): a row with no image would read as no row
    List<String> rows = jdbc.sql("SELECT image FROM " + owner.table + " WHERE id = UUID_TO_BIN(:id) FOR UPDATE")
        .param("id", id.toString()).query((rs, i) -> rs.getString("image")).list();
    if (rows.isEmpty()) throw ApiException.notFound();
    String old = rows.getFirst();
    switch (owner) {
      case PLAYER -> {
        if (!players.canEdit(userId, players.getRequired(id))) throw ApiException.forbidden();
      }
      case TEAM -> access.requireAdmin(userId, ResourceType.TEAM, id);
      case COMPETITION -> access.requireAdmin(userId, ResourceType.COMPETITION, id);
    }
    String saved = file == null ? null : save(file);
    jdbc.sql("UPDATE " + owner.table + " SET image = :image WHERE id = UUID_TO_BIN(:id)")
        .param("image", saved).param("id", id.toString()).update();
    store.deleteAfterCommit(old);
    return saved;
  }

  private String save(MultipartFile file) {
    try {
      return store.save(file.getInputStream());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
