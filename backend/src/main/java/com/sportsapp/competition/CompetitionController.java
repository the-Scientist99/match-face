package com.sportsapp.competition;

import com.sportsapp.common.Auth;
import com.sportsapp.competition.CompetitionDtos.AddTeamRequest;
import com.sportsapp.competition.CompetitionDtos.CompetitionResponse;
import com.sportsapp.competition.CompetitionDtos.CompetitionSummary;
import com.sportsapp.competition.CompetitionDtos.CreateCompetitionRequest;
import com.sportsapp.competition.CompetitionDtos.PatchCompetitionRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Writes commit first, then the response is read fresh. */
@RestController
@RequestMapping("/api/competitions")
public class CompetitionController {

  private final CompetitionService service;

  public CompetitionController(CompetitionService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CompetitionResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCompetitionRequest req) {
    UUID me = Auth.userId(jwt);
    return service.get(me, service.create(me, req));
  }

  @GetMapping("/mine")
  List<CompetitionSummary> mine(@AuthenticationPrincipal Jwt jwt) {
    return service.mine(Auth.userId(jwt));
  }

  @GetMapping("/{id}")
  CompetitionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.get(Auth.userIdOrNull(jwt), id);
  }

  @PatchMapping("/{id}")
  CompetitionResponse patch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody PatchCompetitionRequest req) {
    UUID me = Auth.userId(jwt);
    service.patch(me, id, req);
    return service.get(me, id);
  }

  @PostMapping("/{id}/teams")
  CompetitionResponse addTeam(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody AddTeamRequest req) {
    UUID me = Auth.userId(jwt);
    service.addTeam(me, id, req);
    return service.get(me, id);
  }

  @DeleteMapping("/{id}/teams/{teamId}")
  CompetitionResponse removeTeam(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID teamId) {
    UUID me = Auth.userId(jwt);
    service.removeTeam(me, id, teamId);
    return service.get(me, id);
  }

  @PostMapping("/{id}/generate")
  CompetitionResponse generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    UUID me = Auth.userId(jwt);
    service.generate(me, id);
    return service.get(me, id);
  }
}
