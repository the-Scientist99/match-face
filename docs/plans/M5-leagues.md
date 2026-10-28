# M5 — Leagues Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An organizer creates a league, adds teams, generates round-robin fixtures, and everyone who can see the league gets a live table and stats.

**Architecture:**
- A competition owns stages, and a league has exactly one `ROUND_ROBIN` stage. Matches point to their stage through `stage_id`.
- Fixture generation and standings are pure Java (`RoundRobin`, `Standings`) with unit tests. The API layer only loads data and persists it.
- Competition visibility extends `VisibilitySql`, so every existing read check and stats query picks up league matches with no further changes.

**Tech Stack, Global Constraints, conventions:** [00-main-plan.md](00-main-plan.md)

**Spec:** `docs/superpowers/specs/2026-10-28-sports-league-app-design.md` (sections 3, 4 Competitions, 5 Lifecycle/Leagues/Lineups, 6)

---

### Task 1: Competition schema and round-robin generator

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__competitions.sql`
- Create: `backend/src/main/java/com/sportsapp/competition/{RoundRobin,Pairing}.java`
- Test: `backend/src/test/java/com/sportsapp/competition/RoundRobinTest.java`

**Interfaces:**
- Produces `record Pairing(UUID home, UUID away)`.
- Produces `RoundRobin.schedule(List<UUID> teams, boolean doubleRound): List<List<Pairing>>`. The outer list is the rounds (round number = index + 1); a team with a bye in a round is simply absent from it.

- [ ] **Step 1: Write `V5__competitions.sql`.**

```sql
CREATE TABLE competition (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL, sport VARCHAR(20) NOT NULL, type VARCHAR(10) NOT NULL,
  visibility VARCHAR(10) NOT NULL, status VARCHAR(10) NOT NULL, double_round_robin BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE competition_team (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  competition_id BINARY(16) NOT NULL, team_id BINARY(16) NOT NULL, seed INT NULL,
  UNIQUE (competition_id, team_id), UNIQUE (competition_id, seed), INDEX (team_id),
  FOREIGN KEY (competition_id) REFERENCES competition(id) ON DELETE CASCADE,
  FOREIGN KEY (team_id) REFERENCES team(id)
);
CREATE TABLE stage (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  competition_id BINARY(16) NOT NULL, type VARCHAR(20) NOT NULL, name VARCHAR(50) NOT NULL, sequence_no INT NOT NULL,
  INDEX (competition_id),
  FOREIGN KEY (competition_id) REFERENCES competition(id) ON DELETE CASCADE
);
ALTER TABLE sport_match ADD CONSTRAINT fk_match_stage FOREIGN KEY (stage_id) REFERENCES stage(id) ON DELETE CASCADE;
```

- [ ] **Step 2: Write the failing tests** (`RoundRobinTest`; build team ids with `UUID.randomUUID()`). For every n in 2..20, single round:

```java
@ParameterizedTest @ValueSource(ints = {2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20})
void singleRoundProperties(int n) {
  var rounds = RoundRobin.schedule(teams(n), false);
  assertThat(rounds).hasSize(n % 2 == 0 ? n - 1 : n);
  // each team at most once per round; every unordered pair meets exactly once; n*(n-1)/2 pairings in total
  // home/away balance per team: |home - away| <= 1 for even n, == 0 for odd n
}
@Test void doubleRoundMirrorsFirstHalf()  // n=4: 6 rounds; round k+3 has each pairing of round k with home/away swapped
@Test void oddCountGivesOneByePerRound()  // n=5: every round has 2 pairings; each team absent from exactly one round
@Test void lessThanTwoTeamsRejected()     // n=1 → IllegalArgumentException
```

- [ ] **Step 3:** Run `./mvnw -q test -Dtest=RoundRobinTest`. Expected: FAIL.
- [ ] **Step 4: Implement the circle method.** This exact rule was checked by simulation for n = 2…20, and it meets the balance assertions:

```
list = teams; if odd, insert a BYE (null) at index 0   // the bye must take the fixed slot, or odd-n balance breaks
n = list.size
for r in 0 .. n-2:
  for i in 0 .. n/2-1:
    a = list[i]; b = list[n-1-i]
    (home, away) = i == 0 ? (r even ? (a,b) : (b,a)) : (i odd ? (a,b) : (b,a))
    skip the pair if either is BYE
  list = [list[0], list[n-1], list[1..n-2]]            // rotate everything except index 0
double: append the same rounds again with home/away swapped
```

- [ ] **Step 5:** Run `./mvnw -q test`. Expected: all PASS, and V5 applies.
- [ ] **Step 6:** Commit: `feat: competition schema and round-robin generator`.

### Task 2: Standings calculator

**Files:**
- Create: `backend/src/main/java/com/sportsapp/competition/{Standings,TeamRef,Result,StandingRow}.java`
- Test: `backend/src/test/java/com/sportsapp/competition/StandingsTest.java`

**Interfaces:**
- Consumes: `SportRules.points`, `SportRules.tiebreakers`.
- Produces records: `TeamRef(UUID id, String name)`, `Result(UUID home, UUID away, int homeGoals, int awayGoals)`, and `StandingRow(int position, UUID teamId, String name, int played, int won, int drawn, int lost, int goalsFor, int goalsAgainst, int goalDifference, int points)`.
- Produces `Standings.compute(List<TeamRef> teams, List<Result> results, SportRules rules): List<StandingRow>`.
  - Every team appears, even with 0 played.
  - Positions are 1..n with no shared positions.

- [ ] **Step 1: Write the failing tests:**

```java
@Test void noResultsSortedByName()        // teams "b", "A", "C" → order A, b, C (case-insensitive); all zeros
@Test void countsAndPoints()              // A 2-0 B, A 1-1 C → A: P2 W1 D1 L0 GF3 GA1 GD2 Pts4; B: P1 L1 Pts0
@Test void goalDifferenceBeforeGoalsFor() // A and B on 3 pts; A GD +3 GF 3, B GD +1 GF 5 → A above B
@Test void goalsForBreaksEqualGd()        // equal pts and GD, A GF 4 vs B GF 2 → A above B
@Test void headToHeadBreaksFullTie() {
  // B 1-0 A, A 2-1 C, C 2-1 B → all 3 pts, GD 0; C has GF 3 → first; A and B tied on GF 2; B beat A
  assertThat(names(compute(...))).containsExactly("C", "B", "A");
}
@Test void threeWayHeadToHeadTieFallsBackToName() // A 1-0 B, B 1-0 C, C 1-0 A → A, B, C
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest=StandingsTest`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  1. Accumulate each team's row from the results.
  2. Sort by the tiebreakers in `rules.tiebreakers()` order until `HEAD_TO_HEAD`.
  3. Split the list into groups that are equal on every criterion before `HEAD_TO_HEAD`. Within each group of 2 or more, compute points using only results between members of that group.
  4. Order each group by those points, then `NAME` (`String.CASE_INSENSITIVE_ORDER`).
- [ ] **Step 4:** Run `./mvnw -q test`. Expected: all PASS.
- [ ] **Step 5:** Commit: `feat: standings with head-to-head tiebreak`.

### Task 3: Competitions API, fixtures, access

**Files:**
- Create: `backend/src/main/java/com/sportsapp/competition/{Competition,CompetitionTeam,Stage,CompetitionType,CompetitionStatus,StageType,CompetitionRepository,CompetitionTeamRepository,StageRepository,CompetitionService,FixtureService,CompetitionController,CompetitionDtos}.java`
- Modify: `access/{AccessService,VisibilitySql}.java`, `match/{MatchService,MatchViews}.java`
- Modify: `IntegrationTest` (helper `createLeague`)
- Test: `backend/src/test/java/com/sportsapp/competition/{CompetitionApiTest,CompetitionAccessTest}.java`

**Interfaces:**
- Consumes: `RoundRobin`, `TeamService.addToRoster/rosterIds/name`, `AccessService`.
- Produces enums:
  - `CompetitionType { LEAGUE, CUP }`
  - `CompetitionStatus { DRAFT, ACTIVE }`
  - `StageType { ROUND_ROBIN, KNOCKOUT }`
- Produces DTOs:
  - `CreateCompetitionRequest(@NotBlank @Size(max=80) name, @NotNull CompetitionType type, @NotNull Visibility visibility, boolean doubleRoundRobin)`
  - `PatchCompetitionRequest(@NotNull Long version, @Size(max=80) String name, Visibility visibility, Boolean doubleRoundRobin)`
  - `AddTeamRequest(@NotNull UUID teamId, @Min(1) @Max(64) Integer seed)`
  - `CompetitionTeamView(UUID teamId, String name, Integer seed)`
  - `CompetitionResponse(UUID id, long version, String name, Sport sport, CompetitionType type, Visibility visibility, CompetitionStatus status, boolean doubleRoundRobin, List<CompetitionTeamView> teams, boolean canAdmin, boolean completed)`
    - `completed` = at least one match exists and all of them are `FINISHED` or `CANCELLED`.
  - `CompetitionSummary(UUID id, String name, CompetitionType type, CompetitionStatus status)`
- Produces endpoints:
  - `POST /api/competitions` → 201, plus a `COMPETITION` `OWNER` grant
  - `GET /api/competitions/{id}`
  - `GET /api/competitions/mine`: admin or member, by name
  - `PATCH /api/competitions/{id}`
  - `POST /api/competitions/{id}/teams` → `CompetitionResponse`
  - `DELETE /api/competitions/{id}/teams/{teamId}` → `CompetitionResponse`
  - `POST /api/competitions/{id}/generate` → `CompetitionResponse`
- Produces in `FixtureService`:
  - `void generate(Competition c)`: deletes the existing stages, which cascades to their matches. Then it creates `Stage(ROUND_ROBIN, "League", sequence_no 1)` and `SCHEDULED` matches with `round`. Teams are ordered by seed asc (nulls last), then by when they were added.
  - M6 adds the `CUP` branch.
- Produces `VisibilitySql.VISIBLE_COMPETITION_IDS`:

```sql
SELECT c.id FROM competition c WHERE c.visibility = 'PUBLIC'
  OR EXISTS (SELECT 1 FROM admin_grant g WHERE g.resource_type = 'COMPETITION' AND g.resource_id = c.id AND g.user_id = UUID_TO_BIN(:viewerId))
  OR EXISTS (SELECT 1 FROM competition_team ct JOIN team_player tp ON tp.team_id = ct.team_id JOIN player p ON p.id = tp.player_id
             WHERE ct.competition_id = c.id AND p.user_id = UUID_TO_BIN(:viewerId))
```

  - `VISIBLE_MATCH_IDS` gains `UNION SELECT m.id FROM sport_match m JOIN stage s ON s.id = m.stage_id WHERE s.competition_id IN (VISIBLE_COMPETITION_IDS)`.
- Produces the `AccessService` extensions:
  - `COMPETITION`: `canAdmin` = grant; `canRead` = in `VISIBLE_COMPETITION_IDS`.
  - `MATCH` `canAdmin` adds: a grant on the match's competition.
  - `TEAM` `canRead` adds: the team is in a readable competition.
- Produces the match changes:
  - **Lineup** for a competition match: the allowed players for a side are `myPlayerIds(admin) ∪ rosterIds(sideTeam)`. A chosen player not on that side's roster is added to it with `addToRoster`.
  - **`/api/matches/mine`** also includes competition matches where my player is on either team's roster, or where I admin the competition.
  - **`MatchViews`** fills `competitionId`, `competitionName` and `round`; `visibility` is the competition's.
- Produces helper `IntegrationTest.createLeague(TestUser owner, Visibility v, UUID... teamIds): UUID`. It creates the league, adds the teams and generates fixtures.

**Lifecycle rules** (spec section 5):
- **Add or remove a team:**
  - `DRAFT`: just do it.
  - `ACTIVE` with no `FINISHED` match: do it, then regenerate.
  - Otherwise → `409 TEAMS_LOCKED`.
  - Other errors: a duplicate team → `409 TEAM_ALREADY_ADDED`, a duplicate seed → `409 DUPLICATE_SEED`, a team I don't admin → `403`/`404` via `requireAdmin(TEAM)`.
- **Generate:** fewer than 2 teams → `409 NOT_ENOUGH_TEAMS`; any `FINISHED` match → `409 TEAMS_LOCKED`; otherwise regenerate and set status `ACTIVE`.
- **Patch:**
  - `doubleRoundRobin` outside `DRAFT` → `409 COMPETITION_STARTED`.
  - A stale version → `409 STALE`.
  - `PATCH /api/matches/{id}` with `visibility` on a competition match → `400 INVALID_FIELD`.

- [ ] **Step 1: Write the failing tests.**

`CompetitionApiTest`:
```java
@Test void createLeagueIsDraft()                // → 201 $.status "DRAFT", $.canAdmin true, $.teams empty
@Test void generateFourTeamLeague()             // 4 teams → $.status "ACTIVE"; jdbc: 6 sport_match rows across 3 distinct rounds, all SCHEDULED
@Test void generateDoubleRound()                // doubleRoundRobin true, 3 teams → 6 matches, 6 rounds
@Test void notEnoughTeams()                     // 1 team → 409 NOT_ENOUGH_TEAMS
@Test void addTeamWhileActiveWithoutResultsRegenerates() // 3 teams generated (3 matches) → add 4th → 6 matches; old match ids gone
@Test void teamsLockedAfterAResult()            // finish one match → add team → 409 TEAMS_LOCKED; remove team → 409; generate → 409
@Test void duplicateTeamAndSeed()               // same team twice → 409 TEAM_ALREADY_ADDED; seed 1 twice → 409 DUPLICATE_SEED
@Test void cannotAddTeamYouDoNotAdmin()         // ivan's team → 404 (unreadable)
@Test void doubleRoundLockedAfterDraft()        // PATCH doubleRoundRobin after generate → 409 COMPETITION_STARTED
@Test void lineupAutoAddsToRoster()             // add ana's guest (not on Alpha's roster) to Alpha's side → 200; guest now on Alpha roster
@Test void competitionMatchVisibilityPatchRejected() // PATCH match {visibility} → 400 INVALID_FIELD
```

`CompetitionAccessTest`:
```java
@Test void privateLeagueVisibleToRosterMembersOnly() // ivan's player on Alpha roster (jdbc): GET competition → 200 canAdmin false; stranger/anonymous → 404
@Test void publicLeagueAnonymous()              // anonymous GET competition and one of its matches → 200
@Test void competitionAdminAdminsItsMatches()   // owner posts an event on a league match → 201; roster member → 403
@Test void teamReadableThroughPublicLeague()    // stranger GET /api/teams/{alpha} → 200 when league PUBLIC; 404 when PRIVATE
@Test void privateLeagueMatchHiddenInStrangerProfileStats() // A's goal in private league match not counted for stranger viewer
@Test void mineIncludesLeagueMatchesForRosterMembers()      // ivan (on Alpha roster) sees Alpha's league matches in /api/matches/mine
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest='CompetitionApiTest,CompetitionAccessTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement. Generation, regeneration and team changes are each `@Transactional`.
- [ ] **Step 4:** Run `./mvnw -q test`. Expected: all PASS, including every M2–M4 test. That shows the visibility union didn't loosen one-off rules.
- [ ] **Step 5:** Commit: `feat: leagues, fixtures and competition access`.

### Task 4: Standings, competition stats, fixtures list

**Files:**
- Create: `backend/src/main/java/com/sportsapp/stats/CompetitionStatsQuery.java`
- Modify: `competition/{CompetitionController,CompetitionDtos}.java`
- Test: `backend/src/test/java/com/sportsapp/competition/LeagueTableApiTest.java`

**Interfaces:**
- Consumes: `Standings.compute`, `ScoreSql` (M3), `MatchViews.summaries` (M3), `requireRead(COMPETITION)`.
- Produces DTOs:
  - `StatLeader(UUID playerId, String name, int value)`
  - `CardRow(UUID playerId, String name, int yellow, int red)`
  - `CompetitionStatsResponse(List<StatLeader> topScorers, List<StatLeader> topAssists, List<CardRow> cards)`
- Produces endpoints:
  - `GET /api/competitions/{id}/matches` → `List<MatchSummary>`, ordered by round, then kickoff (nulls last), then `created_at`
  - `GET /api/competitions/{id}/standings` → `List<StandingRow>`; a cup → `400 NOT_A_LEAGUE`
  - `GET /api/competitions/{id}/stats` → `CompetitionStatsResponse`
    - Top 10 of each list, from `FINISHED` matches only, value desc then name.
    - Cards are ordered by red desc, yellow desc, name. Players with zero cards aren't listed.

- [ ] **Step 1: Write the failing tests** (`LeagueTableApiTest`, a 3-team league Alpha/Beta/Gamma):

```java
@Test void tableBeforeAnyResult()            // 3 rows, all zeros, ordered Alpha, Beta, Gamma
@Test void tableAfterResults()               // Alpha 2-0 Beta (own goal by Beta player counts for Alpha), finished → Alpha P1 W1 GF2 Pts3 position 1; Beta L1 position 3
@Test void unfinishedMatchesIgnored()        // goals in a SCHEDULED match → table unchanged; stats unchanged
@Test void unknownScorerCountsForTeamNotPlayer() // goal with no player → Alpha GF 1; topScorers empty
@Test void statsLeaders()                    // Marko 2 goals, Ivan 1 goal + 1 assist, Ivan yellow, Luka red →
                                             // topScorers [Marko 2, Ivan 1]; topAssists [Ivan 1]; cards [Luka 0/1, Ivan 1/0]
@Test void matchesOrderedByRound()           // round values non-decreasing
@Test void standingsOnCup()                  // create a CUP competition (no generation needed) → standings → 400 NOT_A_LEAGUE
@Test void privateTableHiddenFromStranger()  // stranger → 404
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest=LeagueTableApiTest`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Standings load the competition's teams, plus one row per finished `ROUND_ROBIN` match with `ScoreSql` goals, and pass them to `Standings.compute(…, SportRules.of(c.sport))`.
  - Stats use one query per list, joining `match_event` → `sport_match` → `stage` filtered by `competition_id` and `status = 'FINISHED'`.
- [ ] **Step 4:** Run `./mvnw -q test`. Expected: all PASS.
- [ ] **Step 5:** Commit: `feat: league table, fixtures and stats endpoints`.

### Task 5: Frontend competitions

**Files:**
- Create: `frontend/src/pages/competitions/{NewCompetitionPage,CompetitionPage,FixturesTab,TableTab,StatsTab,ManageTab}.tsx`
- Modify: `HomePage.tsx` (My competitions), `MatchPage.tsx` (competition name and round link; roster-first lineup options), `Layout.tsx`, `App.tsx`, both locale files, `api/schema.d.ts`

**Interfaces:**
- Consumes: the Task 3 and Task 4 endpoints, `MatchList`, `PlayerPicker`.
- Produces query keys `['competition', id]`, `['competition', id, 'matches'|'standings'|'stats']` and `['competitions','mine']`.
  - Team and generate mutations invalidate `['competition', id]` and `['competition', id, 'matches']`.
  - Finishing a match also invalidates the parent competition's keys.
- Produces routes `/competitions/new` and `/competitions/:id`.

- [ ] **Step 1:** Run `npm run gen:api`.
- [ ] **Step 2: `NewCompetitionPage`.**
  - Fields: name, type (League; Cup is enabled in M6), visibility, and a "home and away" checkbox (`doubleRoundRobin`).
  - After creating, it navigates to the Manage tab.
- [ ] **Step 3: `CompetitionPage` tabs.**
  - **Fixtures:** `MatchList` grouped by round.
  - **Table:** a Mantine `Table` with columns `#`, Team, P, W, D, L, GF, GA, GD, Pts. GF and GA are hidden under 400 px.
  - **Stats:** three small lists.
  - **Manage:** admin only.
    - The team list has remove buttons.
    - "Add team" offers a select from `['teams','mine']`, or "Create team" inline (`POST /api/teams` then add).
    - The Generate button confirms first, with the text `competition.regenerateWarning` when fixtures exist: "This replaces all fixtures and their dates."
- [ ] **Step 4:** `MatchPage` shows "League name · Round N" linking back. For competition matches, `LineupEditor` lists each side's roster first.
- [ ] **Step 5:** Add locale keys `competition.*`, `table.*`, `stats.*`, and `errors.NOT_ENOUGH_TEAMS`, `TEAMS_LOCKED`, `TEAM_ALREADY_ADDED`, `DUPLICATE_SEED`, `COMPETITION_STARTED`, `NOT_A_LEAGUE`. Then run `npm test && npm run build`. Expected: PASS.
- [ ] **Step 6: Verify manually.**
  1. Create a 4-team private league and generate it: 3 rounds of 2 matches.
  2. Enter and finish one result; the table and stats update.
  3. Try to add a fifth team; the translated `TEAMS_LOCKED` error appears.
  4. A roster member logged in elsewhere sees the league on Home.
- [ ] **Step 7:** Commit: `feat: league screens`.
