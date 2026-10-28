# M6 — Cups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An organizer runs a single-elimination cup. Seeded teams get byes, winners advance automatically, and drawn matches are settled by recording the penalty-shootout winner.

**Architecture:**
- The bracket is planned by a pure `Bracket` class. `FixtureService` persists the plan as one `KNOCKOUT` stage, with matches linked by `next_match_id`/`next_slot`.
- Advancement and reopening live inside M3's `MatchService.finish/reopen/cancel`, and apply only to knockout matches.

**Tech Stack, Global Constraints, conventions:** [00-main-plan.md](00-main-plan.md)

**Spec:** `docs/superpowers/specs/2026-10-28-sports-league-app-design.md` (sections 3, 5 Cups)

---

### Task 1: Bracket planner

**Files:**
- Create: `backend/src/main/java/com/sportsapp/competition/{Bracket,SeededTeam,PlannedMatch,BracketPlan}.java`
- Test: `backend/src/test/java/com/sportsapp/competition/BracketTest.java`

**Interfaces:**
- Produces records:
  - `SeededTeam(UUID teamId, Integer seed)`
  - `PlannedMatch(int round, int position, UUID home, UUID away)`: round is 1-based, position is 0-based within its round; `home`/`away` are null until decided
  - `BracketPlan(int rounds, List<PlannedMatch> matches)`
- Produces `Bracket.seedOrder(int size): List<Integer>`.
- Produces `Bracket.plan(List<SeededTeam> teams, Random random): BracketPlan`.
  - Requires 2..64 teams; otherwise `IllegalArgumentException`.
  - The next match of `(r, p)` is `(r + 1, p / 2)`, in slot `HOME` if `p` is even, otherwise `AWAY`.

- [ ] **Step 1: Write the failing tests.**

```java
@Test void seedOrder() {
  assertThat(Bracket.seedOrder(2)).containsExactly(1, 2);
  assertThat(Bracket.seedOrder(4)).containsExactly(1, 4, 2, 3);
  assertThat(Bracket.seedOrder(8)).containsExactly(1, 8, 4, 5, 2, 7, 3, 6);
}
@Test void twoTeamsIsOneFinal()                 // rounds 1, one match round 1 position 0 with both teams
@Test void sixTeamsTopSeedsGetByes() {
  // seeds 1..6 → rounds 3; round-1 matches at positions 1 (4 v 5) and 3 (3 v 6) only;
  // round 2 position 0 has home = seed 1, position 1 has home = seed 2; round 3 has one match with no teams
}
@Test void matchCountIsTeamsMinusOne()          // for n in 2..20: plan.matches().size() == n - 1
@Test void everyTeamPlacedOnce()                // each team appears exactly once across round-1 matches and bye placements in round 2
@Test void topTwoSeedsMeetOnlyInFinal()         // n = 8: seed 1's round-1 match is at position 0 or 1 (top half), seed 2's at position 2 or 3 (bottom half)
@Test void unseededDrawIsRandomButComplete()    // 5 unseeded teams, new Random(42) → deterministic; same seed gives same plan; all 5 placed
@Test void seededFirstThenUnseeded()            // 4 teams, seeds {X: 1, Y: 2} + two unseeded → round-1 position 0 home = X, position 1 home = Y (seedOrder(4) = [1,4,2,3])
@Test void seedsNeedNotBeContiguous()           // seeds 3 and 7 only → ranked 1st and 2nd
@Test void rejectsOneOrSixtyFiveTeams()
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest=BracketTest`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - **Rank the teams:** seeded teams sorted by seed, then unseeded teams shuffled with `random` (`Collections.shuffle(list, random)`). A team's rank is its 1-based position in that list.
  - **Bracket size:** `size = Integer.highestOneBit(n - 1) << 1` (gives 2 for n = 2, 4 for n = 3–4, 8 for n = 5–8).
  - **Seed order:** `seedOrder(2) = [1, 2]`; `seedOrder(2m)` replaces each `s` in `seedOrder(m)` with `s, 2m + 1 - s`.
  - **Round 1:** for each pair `(seedOrder[2p], seedOrder[2p + 1])`:
    - if the second rank is greater than `n`, place the first team directly in its round-2 slot;
    - otherwise create a round-1 match at position `p`.
  - **Later rounds:** create the empty matches for rounds 2..`rounds`. Bye placements fill their slots.
- [ ] **Step 4:** Run `./mvnw -q test`. Expected: all PASS.
- [ ] **Step 5:** Commit: `feat: knockout bracket planner with seeding and byes`.

### Task 2: Cup generation, advancement and bracket endpoint

**Files:**
- Modify: `competition/{FixtureService,CompetitionService,CompetitionController,CompetitionDtos}.java`, `match/{MatchService,MatchViews}.java`
- Test: `backend/src/test/java/com/sportsapp/competition/CupApiTest.java`

**Interfaces:**
- Consumes: `Bracket.plan`, `FixtureService.generate` (M5), `MatchService.finish/reopen/cancel` (M3), `ScoreSql`.
- Produces in `FixtureService.generate`, `CUP` branch:
  - one `Stage(KNOCKOUT, "Cup", 1)`;
  - matches with `round` and `bracket_position`, plus pre-placed bye teams;
  - `next_match_id`/`next_slot` links, set after all rows exist;
  - the random source is `new SecureRandom()`.
  - More than 64 teams → `409 TOO_MANY_TEAMS`.
- Produces the cup rules in `MatchService`, applied when the match's stage is `KNOCKOUT`:
  - **Lineup, events or finish** while either side's team is null → `409 TEAMS_NOT_SET`.
  - **Finish:**
    - Level score with no `penaltyWinner` → `409 CUP_DRAW_NEEDS_PENALTIES`.
    - Level score: store `penalty_winner_side`. Not level: store null and ignore the request field.
    - Write the winner's team id into the next match's `next_slot` side, in the same transaction.
  - **Reopen:**
    - Allowed only if there is no next match, or the next match is `SCHEDULED` with zero events. Otherwise → `409 LATER_ROUND_FINISHED`.
    - Clears the winner from the next match's slot and deletes that side's lineup rows in the next match.
    - Clears `penalty_winner_side`.
  - **Cancel** → `409 CUP_MATCH_CANNOT_CANCEL`.
- Produces DTOs:
  - `BracketMatch(MatchSummary match, int round, int position, Side penaltyWinner)`
  - `BracketResponse(int rounds, List<BracketMatch> matches, TeamSummary winner)`
    - `winner` is null until the final is `FINISHED`.
    - `TeamSummary` is from M2.
- Produces endpoint: `GET /api/competitions/{id}/bracket` → `BracketResponse`. A league → `400 NOT_A_CUP`.
- Produces helper `IntegrationTest.createCup(TestUser owner, Visibility v, UUID... teamIdsInSeedOrder): UUID`. It seeds teams 1..n in argument order, then generates.

- [ ] **Step 1: Write the failing tests** (`CupApiTest`; each match needs a lineup with one player per side before events are posted):

```java
@Test void generateSixTeamCup()                  // → 5 matches; bracket: round 1 has 2 matches; round 2 matches have seeds 1 and 2 as home teams
@Test void winnerAdvancesToNextSlot()            // finish 4-v-5 match 1-0 for seed 4 → round 2 position 0 away team = seed 4
@Test void drawNeedsPenalties()                  // 1-1, finish without penaltyWinner → 409 CUP_DRAW_NEEDS_PENALTIES; with AWAY → winner = away team advances; $.penaltyWinner "AWAY"
@Test void penaltyWinnerIgnoredWhenNotLevel()    // 2-1 with penaltyWinner AWAY → home advances; penaltyWinner null
@Test void undecidedMatchIsLocked()              // final before semis: PUT lineup → 409 TEAMS_NOT_SET; finish → 409 TEAMS_NOT_SET
@Test void reopenClearsNextSlotAndItsLineup()    // finish match, set next match lineup for that side, reopen → next match slot null, that side's lineup empty
@Test void reopenBlockedWhenNextMatchStarted()   // next match has an event → reopen → 409 LATER_ROUND_FINISHED; next match finished → same
@Test void cupMatchCannotBeCancelled()           // → 409 CUP_MATCH_CANNOT_CANCEL
@Test void finalDecidesWinner()                  // 2-team cup: finish 3-0 → bracket.winner.id = home team; competition completed true
@Test void unseededCupPlacesAllTeams()           // 5 teams without seeds → 4 matches; every team appears in round 1 or round 2
@Test void bracketOnLeague()                     // league → 400 NOT_A_CUP
@Test void cupTeamsLockAfterResult()             // shared M5 lifecycle: add team after a finished match → 409 TEAMS_LOCKED
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest=CupApiTest`. Expected: FAIL.
- [ ] **Step 3:** Implement. Calculate the score for finish with `SportRules.score` over the match's events. That's the same rule `ScoreSql` mirrors.
- [ ] **Step 4:** Run `./mvnw -q test`. Expected: all PASS, including every M3 lifecycle test (one-off behaviour unchanged).
- [ ] **Step 5:** Commit: `feat: cups with advancement, penalties and reopen rules`.

### Task 3: Frontend cups

**Files:**
- Create: `frontend/src/pages/competitions/BracketTab.tsx`, `frontend/src/pages/matches/FinishDialog.tsx`
- Modify: `NewCompetitionPage.tsx` (enable Cup), `CompetitionPage.tsx` (Bracket tab for cups instead of Table), `ManageTab.tsx` (seed input per team, cups only), `MatchPage.tsx` (Finish opens `FinishDialog`), both locale files, `api/schema.d.ts`

**Interfaces:**
- Consumes: `GET /bracket`, `POST /finish` with `penaltyWinner`.
- Produces query key `['competition', id, 'bracket']`. Finishing or reopening a cup match also invalidates it.

- [ ] **Step 1:** Run `npm run gen:api`.
- [ ] **Step 2: `BracketTab`.**
  - One column per round, scrolling horizontally on phones (`ScrollArea`).
  - Round labels: `bracket.final`, `bracket.semifinal`, `bracket.quarterfinal`, else `bracket.round` with the number.
  - Each card shows both sides, using "TBD" or "bye" when empty, plus the score and a "(pens)" marker on the penalty winner.
  - Cards link to the match.
  - The winner banner appears when `winner` is set.
- [ ] **Step 3: `FinishDialog`.**
  - When the match is in a cup and the score is level, it asks "Who won on penalties?" with the two side names as buttons before calling finish.
  - Otherwise it's a plain confirm.
- [ ] **Step 4: Manage tab.** For cups, an optional seed `NumberInput` (1–64) appears next to each team, with hint `competition.seedHint`: "Leave empty for a random draw." Changing a seed means removing and re-adding the team.
- [ ] **Step 5:** Add locale keys `bracket.*`, `errors.CUP_DRAW_NEEDS_PENALTIES`, `LATER_ROUND_FINISHED`, `TEAMS_NOT_SET`, `CUP_MATCH_CANNOT_CANCEL`, `TOO_MANY_TEAMS`, `NOT_A_CUP`. Then run `npm test && npm run build`. Expected: PASS.
- [ ] **Step 6: Verify manually.**
  1. Create a 6-team cup with seeds for 2 teams and generate it.
  2. Seeds 1 and 2 show byes.
  3. Play a quarterfinal to 1-1 and pick the penalty winner; they appear in the semifinal.
  4. Reopen it; the semifinal slot empties.
  5. Play through to the final; the winner banner shows.
- [ ] **Step 7:** Commit: `feat: cup bracket screens`.
