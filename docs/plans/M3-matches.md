# M3 — One-off Matches Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An organizer creates a pickup or team-vs-team match, picks lineups and enters goals, cards and man of the match from a phone, then finishes it. This is the first release real users can use.

**Architecture:**
- A match is a `sport_match` row plus `match_lineup` and an event log in `match_event`. The score is always calculated from the events by `SportRules`.
- Visibility for one-off matches lives in `VisibilitySql.VISIBLE_MATCH_IDS`, the single source that every read check and stats query uses. M5 extends it with competition matches.

**Tech Stack, Global Constraints, conventions:** [00-main-plan.md](00-main-plan.md)

**Spec:** `docs/superpowers/specs/2026-10-28-sports-league-app-design.md` (sections 3, 4 Matches, 6, 7 Frontend, 8)

---

### Task 1: Match schema and football rules

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__matches.sql`
- Create: `backend/src/main/java/com/sportsapp/sport/{SportRules,FootballRules,Score,ScoreEvent,Tiebreaker}.java`
- Create: `backend/src/main/java/com/sportsapp/match/{Side,EventType,MatchStatus}.java`
- Test: `backend/src/test/java/com/sportsapp/sport/FootballRulesTest.java`

**Interfaces:**
- Produces enums:
  - `Side { HOME, AWAY; Side opposite() }`
  - `EventType { GOAL, OWN_GOAL, YELLOW, RED, MOTM }`
  - `MatchStatus { SCHEDULED, FINISHED, CANCELLED }`
  - `Tiebreaker { POINTS, GOAL_DIFFERENCE, GOALS_FOR, HEAD_TO_HEAD, NAME }`
- Produces records: `ScoreEvent(EventType type, Side side)`, `Score(int home, int away)`.
- Produces the rules interface:

```java
public interface SportRules {
  Set<EventType> allowedEventTypes();
  Score score(List<ScoreEvent> events);
  int points(int goalsFor, int goalsAgainst);
  List<Tiebreaker> tiebreakers();
  static SportRules of(Sport sport) { return switch (sport) { case FOOTBALL -> FootballRules.INSTANCE; }; }
}
```

  - Clean sheets aren't part of `SportRules`: they're calculated in the stats SQL (M4), which is football-specific anyway.

- [ ] **Step 1: Write `V3__matches.sql`.**

```sql
CREATE TABLE sport_match (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
  sport VARCHAR(20) NOT NULL, stage_id BINARY(16) NULL, round INT NULL, bracket_position INT NULL,
  home_team_id BINARY(16) NULL, away_team_id BINARY(16) NULL,
  home_label VARCHAR(30) NULL, away_label VARCHAR(30) NULL,
  kickoff_at DATETIME(6) NULL, venue VARCHAR(100) NULL, status VARCHAR(20) NOT NULL,
  visibility VARCHAR(10) NULL, penalty_winner_side VARCHAR(4) NULL,
  next_match_id BINARY(16) NULL, next_slot VARCHAR(4) NULL,
  INDEX (stage_id), INDEX (kickoff_at),
  FOREIGN KEY (home_team_id) REFERENCES team(id), FOREIGN KEY (away_team_id) REFERENCES team(id),
  FOREIGN KEY (next_match_id) REFERENCES sport_match(id) ON DELETE SET NULL
);
CREATE TABLE match_lineup (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  match_id BINARY(16) NOT NULL, player_id BINARY(16) NOT NULL, side VARCHAR(4) NOT NULL, in_goal BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (match_id, player_id), INDEX (player_id),
  FOREIGN KEY (match_id) REFERENCES sport_match(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES player(id)
);
CREATE TABLE match_event (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  match_id BINARY(16) NOT NULL, type VARCHAR(10) NOT NULL, side VARCHAR(4) NOT NULL,
  player_id BINARY(16) NULL, assist_player_id BINARY(16) NULL,
  minute SMALLINT NULL CHECK (minute BETWEEN 0 AND 150),
  INDEX (match_id), INDEX (player_id), INDEX (assist_player_id),
  FOREIGN KEY (match_id) REFERENCES sport_match(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES player(id), FOREIGN KEY (assist_player_id) REFERENCES player(id)
);
```

  - `stage_id` gets its foreign key in M5.

- [ ] **Step 2: Write the failing tests** (`FootballRulesTest`):

```java
@Test void ownGoalCountsForOppositeSide() {
  assertThat(rules.score(List.of(new ScoreEvent(GOAL, HOME), new ScoreEvent(OWN_GOAL, HOME), new ScoreEvent(GOAL, AWAY))))
      .isEqualTo(new Score(1, 2));
}
@Test void cardsAndMotmDoNotScore()     // [YELLOW HOME, RED AWAY, MOTM HOME] → Score(0, 0)
@Test void emptyIsNilNil()              // [] → Score(0, 0)
@Test void points()                     // points(3,1)=3, points(2,2)=1, points(0,1)=0
@Test void tiebreakerOrder()            // [POINTS, GOAL_DIFFERENCE, GOALS_FOR, HEAD_TO_HEAD, NAME]
@Test void allEventTypesAllowed()       // EnumSet.allOf(EventType.class)
```

- [ ] **Step 3:** Run `./mvnw -q test -Dtest=FootballRulesTest`. Expected: FAIL.
- [ ] **Step 4:** Implement `FootballRules` as an enum singleton (`INSTANCE`).
- [ ] **Step 5:** Run `./mvnw -q test`. Expected: all PASS, and Flyway applies V3.
- [ ] **Step 6:** Commit: `feat: match schema and football rules`.

### Task 2: Matches, lineups and match visibility

**Files:**
- Create: `backend/src/main/java/com/sportsapp/match/{Match,MatchLineup,MatchEvent,MatchRepository,MatchLineupRepository,MatchEventRepository,MatchService,MatchController,MatchDtos,MatchViews}.java`
- Create: `backend/src/main/java/com/sportsapp/access/VisibilitySql.java`
- Modify: `backend/src/main/java/com/sportsapp/access/AccessService.java` (`MATCH` branch)
- Modify: `IntegrationTest` (helpers)
- Test: `backend/src/test/java/com/sportsapp/match/{MatchApiTest,LineupApiTest}.java`

**Interfaces:**
- Consumes: `AccessService`, `PlayerService.myPlayerIds/namesById`, `TeamService.name`, `SportRules.of`.
- Produces entities:
  - `Match`: every column; `@Version long version`; `UUID stageId`, `Integer round`, `Integer bracketPosition`, `Side penaltyWinnerSide`, `UUID nextMatchId`, `Side nextSlot`
  - `MatchLineup(matchId, playerId, side, inGoal)`
  - `MatchEvent(matchId, type, side, playerId, assistPlayerId, minute)`
- Produces DTOs:
  - `CreateMatchRequest(UUID homeTeamId, UUID awayTeamId, @Size(max=30) String homeLabel, @Size(max=30) String awayLabel, Instant kickoffAt, @Size(max=100) String venue, @NotNull Visibility visibility)`
  - `PatchMatchRequest(@NotNull Long version, Instant kickoffAt, String venue, Visibility visibility)`: null means unchanged; venue `""` clears it
  - `LineupEntry(@NotNull UUID playerId, @NotNull Side side, boolean inGoal)`, `SetLineupRequest(@NotNull @Valid List<LineupEntry> entries)`
  - `SideView(UUID teamId, String name)`: name = team name or label
  - `LineupView(UUID playerId, String name, Side side, boolean inGoal)`
  - `EventView(UUID id, EventType type, Side side, UUID playerId, String playerName, UUID assistPlayerId, String assistPlayerName, Integer minute)`
  - `MatchResponse(UUID id, long version, Sport sport, MatchStatus status, Instant kickoffAt, String venue, Visibility visibility, UUID competitionId, String competitionName, Integer round, SideView home, SideView away, Score score, Side penaltyWinner, List<LineupView> lineup, List<EventView> events, boolean canAdmin)`
    - For competition matches, `visibility` is the competition's (M5).
    - `events` are ordered by minute (nulls last), then `created_at`.
  - `MatchSummary(UUID id, MatchStatus status, Instant kickoffAt, String homeName, String awayName, Score score, UUID competitionId, String competitionName, Integer round)`
- Produces endpoints:
  - `POST /api/matches` → 201 `MatchResponse`; the creator gets a `MATCH` `OWNER` grant; status `SCHEDULED`
  - `GET /api/matches/{id}`
  - `PATCH /api/matches/{id}`
  - `PUT /api/matches/{id}/lineup` → `MatchResponse`
  - `GET /api/matches/mine` → `List<MatchSummary>`: matches I hold a grant for or my player is in the lineup of, newest kickoff first, nulls first, then `created_at` desc
- Produces `match/ScoreSql`, the SQL twin of `FootballRules.score`, used for lists, standings and brackets:
  - `HOME_GOALS = "SUM(CASE WHEN (e.type = 'GOAL' AND e.side = 'HOME') OR (e.type = 'OWN_GOAL' AND e.side = 'AWAY') THEN 1 ELSE 0 END)"`
  - `AWAY_GOALS` is the mirror.
  - Both expect `LEFT JOIN match_event e ON e.match_id = m.id` and `GROUP BY m.id`.
- Produces in `MatchViews`:
  - `MatchResponse view(UUID matchId, UUID viewerId)`
  - `List<MatchSummary> summaries(List<UUID> matchIds)`
  - These read via `JdbcClient` and are reused by M4–M6.
- Produces `AccessService` for `MATCH`:
  - `canAdmin`: a `MATCH` grant. M5 adds "or admin of its competition".
  - `canRead`: the id is in `VISIBLE_MATCH_IDS`.
- Produces `VisibilitySql.VISIBLE_MATCH_IDS` (M3 version; M5 adds a `UNION` for competition matches):

```sql
SELECT m.id FROM sport_match m
WHERE m.stage_id IS NULL AND (
  m.visibility = 'PUBLIC'
  OR EXISTS (SELECT 1 FROM admin_grant g WHERE g.resource_type = 'MATCH' AND g.resource_id = m.id AND g.user_id = UUID_TO_BIN(:viewerId))
  OR EXISTS (SELECT 1 FROM match_lineup l JOIN player p ON p.id = l.player_id
             WHERE l.match_id = m.id AND p.user_id = UUID_TO_BIN(:viewerId)))
```

  - When `:viewerId` is null, `UUID_TO_BIN(NULL)` is NULL, so anonymous viewers match only public rows.
- Produces `IntegrationTest` helpers:
  - `UUID createPickup(TestUser owner, Visibility v)`: labels "Bibs"/"Non-bibs"
  - `void setLineup(TestUser owner, UUID matchId, Object... playerIdSidePairs)`: alternating `UUID`, `Side`; no keepers
  - `long version(UUID matchId)`

- [ ] **Step 1: Write the failing tests.**

`MatchApiTest`:
```java
@Test void createPickup()                      // → 201 $.home.name "Bibs", $.away.name "Non-bibs", $.score.home 0, $.status "SCHEDULED", $.canAdmin true, $.version 0
@Test void createTeamMatchNeedsAdminOfBothTeams() // ana admins A, ivan admins B: ana creates A vs B → 404 (B unreadable to ana);
                                                  // put ana's own player on B's roster with jdbc → 403 (readable, not admin)
@Test void sameTeamBothSides()                 // homeTeamId == awayTeamId → 400 $.code "SAME_TEAM"
@Test void sidesMustBeTwoTeamsOrTwoLabels()    // one team + one label, or nothing → 400 $.code "INVALID_SIDES"
@Test void privateMatchVisibility()            // stranger GET → 404; anonymous → 404; ivan whose own player is in the lineup → 200 $.canAdmin false
@Test void publicMatchReadableAnonymously()    // PUBLIC → anonymous 200 $.canAdmin false
@Test void patchDetailsAndStaleVersion()       // PATCH {version:0, venue:"Park"} → 200 $.venue "Park", $.version 1; PATCH again with version 0 → 409 "STALE"
@Test void mineListsGrantedAndLineupMatches()  // ana sees her match; ivan sees it only after his player is in its lineup
```

`LineupApiTest`:
```java
@Test void setLineupFromMyPlayers()            // [guestA HOME inGoal, guestB AWAY] → 200 $.lineup.length() 2, keeper flag kept
@Test void lineupRejectsUnavailablePlayer()    // ivan's guest → 400 "PLAYER_NOT_AVAILABLE"
@Test void lineupRejectsDuplicatePlayer()      // same player twice → 400 "DUPLICATE_PLAYER"
@Test void nonAdminCannotSetLineup()           // lineup member ivan → 403; stranger → 404
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest='MatchApiTest,LineupApiTest'`. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - **Create:** team sides call `requireAdmin(user, TEAM, id)` for each team.
  - **Patch:** compare `request.version` with `match.getVersion()`, otherwise `409 STALE`. `visibility` on a competition match → `400 INVALID_FIELD` (reachable from M5).
  - **Lineup:**
    - Allowed players are `myPlayerIds(admin)`; M5 adds the side's team roster for competition matches.
    - A finished match → `409 MATCH_FINISHED`.
    - The rule for removing or moving players who have events comes in Task 3.

- [ ] **Step 4:** Run `./mvnw -q test`. Expected: all PASS.
- [ ] **Step 5:** Commit: `feat: one-off matches, lineups, match visibility`.

### Task 3: Events, finish, reopen, cancel

**Files:**
- Modify: `backend/src/main/java/com/sportsapp/match/{MatchService,MatchController,MatchDtos}.java`
- Test: `backend/src/test/java/com/sportsapp/match/{EventApiTest,MatchLifecycleTest}.java`

**Interfaces:**
- Produces DTOs:
  - `EventRequest(@NotNull EventType type, @NotNull Side side, UUID playerId, UUID assistPlayerId, @Min(0) @Max(150) Integer minute)`
  - `VersionRequest(@NotNull Long version)`
  - `FinishRequest(@NotNull Long version, Side penaltyWinner)`: `penaltyWinner` is used from M6
- Produces endpoints (each returns `MatchResponse`):
  - `POST /api/matches/{id}/events` → 201
  - `PATCH /api/matches/{id}/events/{eventId}` → 200
  - `DELETE /api/matches/{id}/events/{eventId}` → 200
  - `POST /api/matches/{id}/finish` (`FinishRequest`): `SCHEDULED` → `FINISHED`
  - `POST /api/matches/{id}/reopen` (`VersionRequest`): `FINISHED`/`CANCELLED` → `SCHEDULED`
  - `POST /api/matches/{id}/cancel` (`VersionRequest`): `SCHEDULED` → `CANCELLED`
- Produces in `MatchService`:
  - `void finish(UUID userId, UUID matchId, FinishRequest r)`, `void reopen(UUID userId, UUID matchId, long version)` and `void cancel(UUID userId, UUID matchId, long version)`. M6 adds cup rules inside these three methods.

**Event validation, in order** (first failure wins):
1. The match isn't `SCHEDULED` → `409 MATCH_FINISHED`.
2. `type` isn't in `rules.allowedEventTypes()` → `400 INVALID_EVENT`.
3. `YELLOW`/`RED`/`MOTM` without `playerId` → `400 INVALID_EVENT`. An assist on a non-`GOAL`, an assist without a scorer, or an assister equal to the scorer → `400 INVALID_EVENT`.
4. `playerId` is set but the player isn't in the lineup on `side` (for `OWN_GOAL`, `side` is the scorer's own side), or the assister isn't in the lineup on `side` → `400 PLAYER_NOT_IN_LINEUP`.
5. A second `MOTM` → `400 INVALID_EVENT`.

- [ ] **Step 1: Write the failing tests.**

`EventApiTest`:
```java
@Test void goalWithAssistAndMinuteUpdatesScore()  // GOAL HOME scorer A assist B minute 12 → 201 $.score.home 1, $.events[0].assistPlayerName "B", $.events[0].minute 12
@Test void unknownScorerGoalCounts()              // GOAL AWAY no player → $.score.away 1, $.events[0].playerId null
@Test void ownGoalScoresForOtherSide()            // OWN_GOAL side HOME player A (home) → $.score.away 1
@Test void eventsOrderedByMinuteNullsLast()       // minute 50, null, 10 → minutes [10, 50, null]
@Test void minuteOutOfRange()                     // minute 151 and -1 → 400 VALIDATION
@Test void cardNeedsPlayer()                      // YELLOW without playerId → 400 INVALID_EVENT
@Test void assistRules()                          // assist on YELLOW, assist == scorer, assist without scorer → 400 INVALID_EVENT each
@Test void playerMustBeInLineupOnThatSide()       // GOAL side AWAY with a HOME player → 400 PLAYER_NOT_IN_LINEUP
@Test void onlyOneMotm()                          // second MOTM → 400 INVALID_EVENT
@Test void summaryScoreAgreesWithDetailScore()  // GOAL HOME + OWN_GOAL HOME + GOAL AWAY → GET /mine $[0].score equals GET /{id} $.score (1–2)
@Test void editAndDeleteEvent()                   // PATCH minute → 200; DELETE → 200 $.events empty, $.score.home 0
@Test void removingPlayerWithEventsIsRejected()   // A scored: lineup without A → 409 PLAYER_HAS_EVENTS; lineup still has A
@Test void movingPlayerWithEventsIsRejected()     // B assisted: lineup with B on AWAY → 409 PLAYER_HAS_EVENTS
```

`MatchLifecycleTest`:
```java
@Test void finishThenEditIsBlockedUntilReopen()   // finish → $.status FINISHED; POST event → 409 MATCH_FINISHED; PUT lineup → 409 MATCH_FINISHED;
                                                  // reopen → SCHEDULED; POST event → 201
@Test void finishTwice()                          // second finish (current version) → 409 MATCH_FINISHED
@Test void cancelAndReopen()                      // cancel → CANCELLED; reopen → SCHEDULED
@Test void lifecycleNeedsCurrentVersion()         // finish with old version → 409 STALE
@Test void memberCannotFinish()                   // lineup member → 403
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest='EventApiTest,MatchLifecycleTest'`. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - **Lineup rule:** for every player referenced by an event (as player or assister), the new lineup must still contain that player on the same side, otherwise `409 PLAYER_HAS_EVENTS`.
  - **Versions:** finish, reopen and cancel check the version and bump it with JPA `@Version`. Event writes don't check the version, but they touch the match (`version++`) so that clients editing details see `STALE`.

- [ ] **Step 4:** Run `./mvnw -q test`. Expected: all PASS.
- [ ] **Step 5:** Commit: `feat: match events, finish, reopen, cancel`.

### Task 4: Frontend matches and the result screen

**Files:**
- Create: `frontend/src/pages/matches/{NewMatchPage,MatchPage}.tsx`
- Create: `frontend/src/pages/matches/{LineupEditor,EventEntry,EventList,ScoreHeader}.tsx`
- Create: `frontend/src/components/MatchList.tsx`
- Modify: `frontend/src/pages/home/HomePage.tsx`, `App.tsx`, both locale files, `api/schema.d.ts`

**Interfaces:**
- Consumes: the Task 2 and Task 3 endpoints, and `PlayerPicker` (M2).
- Produces query keys `['match', id]` and `['matches','mine']`. Every match mutation sets `['match', id]` from the returned `MatchResponse` (no refetch) and invalidates `['matches','mine']`.
- Produces `MatchList({ matches: MatchSummary[] })`, reused by competitions in M5.
- Produces routes `/matches/new` and `/matches/:id`.

- [ ] **Step 1:** Run `npm run gen:api`.
- [ ] **Step 2: `NewMatchPage`.**
  - A `SegmentedControl` chooses Pickup or Teams.
  - Pickup: two label inputs, defaulting to `match.defaultHome`/`match.defaultAway` ("Bibs"/"Non-bibs" in `en`).
  - Teams: two selects from `['teams','mine']`.
  - Also kickoff (`<input type="datetime-local">` via Mantine `TextInput type="datetime-local"`, converted to ISO), venue and visibility (Private default).
  - After creating, it navigates to `/matches/:id`.
- [ ] **Step 3: `MatchPage`.**
  - `ScoreHeader` shows the side names, a large score, the status badge and the kickoff time.
  - Admin, `SCHEDULED`: `LineupEditor`, `EventEntry`, `EventList` with delete (undo) buttons, and Finish and Cancel buttons.
  - Admin, `FINISHED`/`CANCELLED`: a Reopen button.
  - Non-admin: read-only score, lineup and events.
  - `STALE` errors refetch the match and show `errors.STALE`.
- [ ] **Step 4: `LineupEditor`.**
  - One `PlayerPicker` per side, with each picker excluding the other side's players, plus an "in goal" checkbox per selected player.
  - Save sends `PUT /lineup`.
- [ ] **Step 5: `EventEntry`.**
  - Both lineups appear as Mantine `Chip`/`Button`s in two columns.
  - Tapping a player opens a `Menu` with Goal / Own goal / Yellow / Red / MOTM.
  - Goal opens a `Modal` with an optional assist `Select` (same-side lineup, scorer excluded), an optional minute `NumberInput` (0–150), and Save and Skip buttons. Both buttons post the goal; Skip sends no assist or minute.
  - A per-side "Goal (unknown scorer)" button posts a `GOAL` with no player.
  - All controls are **disabled while a mutation is pending**, so a double tap can't create duplicate events.
- [ ] **Step 6: `HomePage`.**
  - "New match" button.
  - `MatchList` of `['matches','mine']` split into Upcoming (`SCHEDULED`) and Recent (`FINISHED`), with `CANCELLED` shown in Recent with a badge.
- [ ] **Step 7:** Add locale keys `match.*`, `event.*`, and `errors.SAME_TEAM`, `INVALID_SIDES`, `DUPLICATE_PLAYER`, `PLAYER_NOT_IN_LINEUP`, `PLAYER_HAS_EVENTS`, `MATCH_FINISHED`, `INVALID_EVENT`, `INVALID_FIELD`.
- [ ] **Step 8:** Run `npm test && npm run build`. Expected: PASS.
- [ ] **Step 9: Verify manually** on a phone-sized viewport (DevTools, 390 px wide):
  1. Create a pickup match.
  2. Add 3 players a side and mark keepers.
  3. Enter a goal with assist and minute, a skipped-details goal, an own goal and a yellow card.
  4. Undo one event.
  5. Finish.
  6. Try to add an event; a translated `MATCH_FINISHED` error appears.
  7. Reopen.
  - The whole result entry should take under 2 minutes.
- [ ] **Step 10:** Commit: `feat: match creation and mobile result entry`.
