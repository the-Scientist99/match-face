package com.sportsapp.competition;

import com.sportsapp.common.ApiException;
import com.sportsapp.match.MatchService;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
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
      case CUP -> {
        if (teams.size() > Bracket.MAX_TEAMS) throw ApiException.conflict("TOO_MANY_TEAMS");
        List<SeededTeam> seeded = competitionTeams.findByCompetitionId(c.getId()).stream()
            .map(ct -> new SeededTeam(ct.getTeamId(), ct.getSeed())).toList();
        BracketPlan plan = Bracket.plan(seeded, new SecureRandom());
        Stage stage = stages.save(new Stage(c.getId(), StageType.KNOCKOUT, "Cup", 1));
        Map<String, UUID> ids = new HashMap<>();
        for (PlannedMatch pm : plan.matches()) {
          ids.put(pm.round() + ":" + pm.position(),
              matches.createFixture(stage.getId(), pm.round(), pm.position(), c.getSport(), pm.home(), pm.away()));
        }
        for (PlannedMatch pm : plan.matches()) { // links once every row exists
          if (pm.round() < plan.rounds()) {
            matches.linkNext(ids.get(pm.round() + ":" + pm.position()), ids.get((pm.round() + 1) + ":" + (pm.position() / 2)),
                Bracket.nextSlot(pm.position()));
          }
        }
      }
    }
  }

  @Transactional
  public void deleteFixtures(Competition c) {
    stages.deleteByCompetitionId(c.getId());
  }
}
