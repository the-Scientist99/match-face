package com.sportsapp.match;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.Auth;
import com.sportsapp.match.MatchDtos.CreateMatchRequest;
import com.sportsapp.match.MatchDtos.EventRequest;
import com.sportsapp.match.MatchDtos.FinishRequest;
import com.sportsapp.match.MatchDtos.JoinRequest;
import com.sportsapp.match.MatchDtos.MatchResponse;
import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.match.MatchDtos.PatchMatchRequest;
import com.sportsapp.match.MatchDtos.RsvpRequest;
import com.sportsapp.match.MatchDtos.SetLineupRequest;
import com.sportsapp.match.MatchDtos.VersionRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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

  @GetMapping("/open")
  List<MatchSummary> open(@AuthenticationPrincipal Jwt jwt) {
    return service.open(Auth.userId(jwt));
  }

  @PostMapping("/{id}/join")
  MatchResponse join(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody JoinRequest req) {
    UUID me = Auth.userId(jwt);
    service.join(me, id, req.side());
    return views.view(id, me);
  }

  @PutMapping("/{id}/rsvp")
  MatchResponse rsvp(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RsvpRequest req) {
    UUID me = Auth.userId(jwt);
    service.rsvp(me, id, req.going());
    return views.view(id, me);
  }

  @DeleteMapping("/{id}/join")
  MatchResponse leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    UUID me = Auth.userId(jwt);
    service.leave(me, id);
    return views.view(id, me);
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

  @PostMapping("/{id}/events")
  @ResponseStatus(HttpStatus.CREATED)
  MatchResponse addEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody EventRequest req) {
    UUID me = Auth.userId(jwt);
    service.addEvent(me, id, req);
    return views.view(id, me);
  }

  @PatchMapping("/{id}/events/{eventId}")
  MatchResponse updateEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID eventId,
      @Valid @RequestBody EventRequest req) {
    UUID me = Auth.userId(jwt);
    service.updateEvent(me, id, eventId, req);
    return views.view(id, me);
  }

  @DeleteMapping("/{id}/events/{eventId}")
  MatchResponse deleteEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID eventId) {
    UUID me = Auth.userId(jwt);
    service.deleteEvent(me, id, eventId);
    return views.view(id, me);
  }

  @PostMapping("/{id}/finish")
  MatchResponse finish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody FinishRequest req) {
    UUID me = Auth.userId(jwt);
    service.finish(me, id, req);
    return views.view(id, me);
  }

  @PostMapping("/{id}/reopen")
  MatchResponse reopen(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody VersionRequest req) {
    UUID me = Auth.userId(jwt);
    service.reopen(me, id, req.version());
    return views.view(id, me);
  }

  @PostMapping("/{id}/start")
  MatchResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody VersionRequest req) {
    UUID me = Auth.userId(jwt);
    service.start(me, id, req.version());
    return views.view(id, me);
  }

  @PostMapping("/{id}/cancel")
  MatchResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody VersionRequest req) {
    UUID me = Auth.userId(jwt);
    service.cancel(me, id, req.version());
    return views.view(id, me);
  }

  @PutMapping("/{id}/lineup")
  MatchResponse setLineup(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody SetLineupRequest req) {
    UUID me = Auth.userId(jwt);
    service.setLineup(me, id, req.entries());
    return views.view(id, me);
  }
}
