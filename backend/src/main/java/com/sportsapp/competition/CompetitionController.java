package com.sportsapp.competition;

import com.sportsapp.access.AccessService;
import com.sportsapp.access.ResourceType;
import com.sportsapp.common.Auth;
import com.sportsapp.match.MatchDtos.MatchSummary;
import com.sportsapp.stats.CompetitionStatsQuery;
import com.sportsapp.stats.StatsDtos.CompetitionStatsResponse;
import com.sportsapp.competition.CompetitionDtos.AddTeamRequest;
import com.sportsapp.competition.CompetitionDtos.BracketResponse;
import com.sportsapp.competition.CompetitionDtos.CompetitionResponse;
import com.sportsapp.competition.CompetitionDtos.CompetitionSummary;
import com.sportsapp.competition.CompetitionDtos.CreateCompetitionRequest;
import com.sportsapp.competition.CompetitionDtos.PatchCompetitionRequest;
import com.sportsapp.competition.CompetitionDtos.RoundKickoffRequest;
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

/** Writes commit first, then the response is read fresh. */
@RestController
@RequestMapping("/api/competitions")
public class CompetitionController {

  private final CompetitionService service;
  private final CompetitionStatsQuery stats;
  private final AccessService access;

  public CompetitionController(CompetitionService service, CompetitionStatsQuery stats, AccessService access) {
    this.service = service;
    this.stats = stats;
    this.access = access;
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

  @GetMapping("/{id}/matches")
  List<MatchSummary> matches(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.matches(Auth.userIdOrNull(jwt), id);
  }

  @GetMapping("/{id}/standings")
  List<StandingRow> standings(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.standings(Auth.userIdOrNull(jwt), id);
  }

  @GetMapping("/{id}/bracket")
  BracketResponse bracket(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.bracket(Auth.userIdOrNull(jwt), id);
  }

  @GetMapping("/{id}/stats")
  CompetitionStatsResponse stats(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    access.requireRead(Auth.userIdOrNull(jwt), ResourceType.COMPETITION, id);
    return stats.stats(id);
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

  @PutMapping("/{id}/rounds/{round}/kickoff")
  CompetitionResponse setRoundKickoff(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable int round,
      @Valid @RequestBody RoundKickoffRequest req) {
    UUID me = Auth.userId(jwt);
    service.setRoundKickoff(me, id, round, req.kickoffAt());
    return service.get(me, id);
  }

  @PostMapping("/{id}/generate")
  CompetitionResponse generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    UUID me = Auth.userId(jwt);
    service.generate(me, id);
    return service.get(me, id);
  }
}
