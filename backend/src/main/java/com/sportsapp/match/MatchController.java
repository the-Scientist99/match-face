package com.sportsapp.match;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.Auth;
import com.sportsapp.match.MatchDtos.CreateMatchRequest;
import com.sportsapp.match.MatchDtos.MatchResponse;
import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.match.MatchDtos.PatchMatchRequest;
import com.sportsapp.match.MatchDtos.SetLineupRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Writes commit first, then the response is read fresh, so it shows the bumped version. */
@RestController
@RequestMapping("/api/matches")
public class MatchController {

  private final MatchService service;
  private final MatchViews views;
  private final AccessService access;

  public MatchController(MatchService service, MatchViews views, AccessService access) {
    this.service = service;
    this.views = views;
    this.access = access;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  MatchResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateMatchRequest req) {
    UUID me = Auth.userId(jwt);
    return views.view(service.create(me, req), me);
  }

  @GetMapping("/mine")
  List<MatchSummary> mine(@AuthenticationPrincipal Jwt jwt) {
    return service.mine(Auth.userId(jwt));
  }

  @GetMapping("/{id}")
  MatchResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    UUID viewer = Auth.userIdOrNull(jwt);
    access.requireRead(viewer, ResourceType.MATCH, id);
    return views.view(id, viewer);
  }

  @PatchMapping("/{id}")
  MatchResponse patch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody PatchMatchRequest req) {
    UUID me = Auth.userId(jwt);
    service.patch(me, id, req);
    return views.view(id, me);
  }

  @PutMapping("/{id}/lineup")
  MatchResponse setLineup(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody SetLineupRequest req) {
    UUID me = Auth.userId(jwt);
    service.setLineup(me, id, req.entries());
    return views.view(id, me);
  }
}
