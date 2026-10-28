package com.sportsapp.player;

import com.sportsapp.common.Auth;
import com.sportsapp.player.PlayerDtos.CreatePlayerRequest;
import com.sportsapp.player.PlayerDtos.PlayerResponse;
import com.sportsapp.player.PlayerDtos.RenamePlayerRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

  private final PlayerService players;

  public PlayerController(PlayerService players) {
    this.players = players;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  PlayerResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePlayerRequest req) {
    UUID me = Auth.userId(jwt);
    return players.response(me, players.createGuest(me, req.name()));
  }

  @PatchMapping("/{id}")
  PlayerResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RenamePlayerRequest req) {
    UUID me = Auth.userId(jwt);
    return players.response(me, players.rename(me, id, req.name()));
  }

  @GetMapping("/mine")
  List<PlayerResponse> mine(@AuthenticationPrincipal Jwt jwt) {
    return players.mine(Auth.userId(jwt));
  }

  @GetMapping("/{id}")
  PlayerResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return players.response(Auth.userIdOrNull(jwt), players.getRequired(id));
  }
}
