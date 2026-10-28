package com.sportsapp.team;

import com.sportsapp.common.Auth;
import com.sportsapp.team.TeamDtos.CreateTeamRequest;
import com.sportsapp.team.TeamDtos.RenameTeamRequest;
import com.sportsapp.team.TeamDtos.SetRosterRequest;
import com.sportsapp.team.TeamDtos.TeamResponse;
import com.sportsapp.team.TeamDtos.TeamSummary;
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

@RestController
@RequestMapping("/api/teams")
public class TeamController {

  private final TeamService teams;

  public TeamController(TeamService teams) {
    this.teams = teams;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  TeamResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTeamRequest req) {
    return teams.create(Auth.userId(jwt), req.name());
  }

  @GetMapping("/mine")
  List<TeamSummary> mine(@AuthenticationPrincipal Jwt jwt) {
    return teams.mine(Auth.userId(jwt));
  }

  @GetMapping("/{id}")
  TeamResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return teams.get(Auth.userIdOrNull(jwt), id);
  }

  @PatchMapping("/{id}")
  TeamResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RenameTeamRequest req) {
    return teams.rename(Auth.userId(jwt), id, req.name());
  }

  @PutMapping("/{id}/players")
  TeamResponse setRoster(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody SetRosterRequest req) {
    return teams.setRoster(Auth.userId(jwt), id, req.playerIds());
  }
}
