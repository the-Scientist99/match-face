package com.sportsapp.competition;

import com.sportsapp.common.ApiException;
import com.sportsapp.match.MatchService;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FixtureService {

  private final StageRepository stages;
  private final CompetitionTeamRepository competitionTeams;
  private final MatchService matches;

  public FixtureService(StageRepository stages, CompetitionTeamRepository competitionTeams, MatchService matches) {
    this.stages = stages;
    this.competitionTeams = competitionTeams;
    this.matches = matches;
  }

  /** Replaces all fixtures: the old stages go (their matches with them), then a fresh stage and its matches. */
  @Transactional
  public void generate(Competition c) {
    deleteFixtures(c);
    List<UUID> teams = competitionTeams.findByCompetitionId(c.getId()).stream()
        .sorted(Comparator.comparing(CompetitionTeam::getSeed, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CompetitionTeam::getCreatedAt))
        .map(CompetitionTeam::getTeamId)
        .toList();
    switch (c.getType()) {
      case LEAGUE -> {
        Stage stage = stages.save(new Stage(c.getId(), StageType.ROUND_ROBIN, "League", 1));
        List<List<Pairing>> rounds = RoundRobin.schedule(teams, c.isDoubleRoundRobin());
        for (int r = 0; r < rounds.size(); r++) {
          for (Pairing p : rounds.get(r)) matches.createFixture(stage.getId(), r + 1, c.getSport(), p.home(), p.away());
        }
      }
      case CUP -> throw ApiException.badRequest("NOT_A_LEAGUE"); // the knockout draw arrives in M6
    }
  }

  @Transactional
  public void deleteFixtures(Competition c) {
    stages.deleteByCompetitionId(c.getId());
  }
}
