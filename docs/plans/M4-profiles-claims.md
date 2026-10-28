# M4 — Player Profiles and Claims Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every player has a profile whose totals and match list count only matches the viewer can see. A guest can be claimed through an invite link, which merges their history into the claiming user's player.

**Architecture:**
- **Stats:** read-only SQL in the `stats` module, always filtered by `VisibilitySql.VISIBLE_MATCH_IDS`.
- **Claims:** `player_claim` holds hashed tokens. Claiming is always a merge, because every user already has a player since M1. The merge runs in one transaction that moves the guest's rows and deletes the guest.

**Tech Stack, Global Constraints, conventions:** [00-main-plan.md](00-main-plan.md)

**Spec:** `docs/superpowers/specs/2026-10-28-sports-league-app-design.md` (sections 3, 6 Player profile and Claiming)

---

### Task 1: Player stats and profile endpoint

**Files:**
- Create: `backend/src/main/java/com/sportsapp/stats/{PlayerStatsQuery,StatsDtos}.java`
- Modify: `backend/src/main/java/com/sportsapp/player/{PlayerController,PlayerDtos}.java`
- Test: `backend/src/test/java/com/sportsapp/stats/PlayerStatsTest.java`

**Interfaces:**
- Consumes (M3): `VisibilitySql.VISIBLE_MATCH_IDS` (binds `:viewerId` as a UUID string or null), `MatchViews.summaries(List<UUID>)` (keeps the given order), `MatchDtos.MatchSummary`, `Side`, `PlayerService.getRequired/canEdit`, `AccessService`, and the `IntegrationTest` helpers `register/createGuest/createTeam/createPickup/setLineup/putOnRoster/version`.
- Adds `IntegrationTest` helpers `addEvent(TestUser, UUID matchId, Object... keyValues)` (POST an event, expect 201) and `finish(TestUser, UUID matchId)`. Keeper lineups in tests are PUT directly, since `setLineup` sets nobody in goal.
- `GET /api/players/{id}` stays open to anonymous viewers; only the stats are filtered.
- Produces DTOs:
  - `PlayerTotals(int appearances, int goals, int assists, int ownGoals, int yellowCards, int redCards, int cleanSheets, int manOfTheMatch)`
  - `PlayerMatchRow(MatchSummary match, Side side, int goals, int assists)`
  - `PlayerProfileResponse(UUID id, String name, boolean claimed, boolean canEdit, boolean canInvite, PlayerTotals totals, List<PlayerMatchRow> matches)`
- Produces in `PlayerStatsQuery`:
  - `PlayerTotals totals(UUID playerId, @Nullable UUID viewerId)`
  - `List<PlayerMatchRow> matches(UUID playerId, @Nullable UUID viewerId)`: kickoff desc (nulls last), then `created_at` desc
  - Both count **`FINISHED` matches only**.
- Produces endpoint: `GET /api/players/{id}` now returns `PlayerProfileResponse`. `canInvite` = the viewer created this guest and it's unclaimed.

- [ ] **Step 1: Write the failing tests** (`PlayerStatsTest`). Use M3 helpers plus direct event and finish calls through MockMvc.

```java
@Test void totalsFromFinishedMatchesOnly()  // A: 2 goals in finished match, 1 goal in scheduled match → goals 2, appearances 1
@Test void assistsOwnGoalsCardsMotm()       // A assists B's goal, scores own goal, gets YELLOW and MOTM → assists 1, ownGoals 1, yellowCards 1, manOfTheMatch 1, goals 0
@Test void cleanSheetRules()                // keeper K (HOME, inGoal): 1-0 win → cleanSheets 1;
                                            // next match: AWAY scores with unknown scorer → 0 more; next: K's teammate own goal → 0 more;
                                            // outfield player on the same side never gets clean sheets
@Test void unknownScorerGoalCreditsNobody() // AWAY goal with no player → no AWAY player has goals > 0
@Test void strangerSeesPublicOnly()         // A plays one PUBLIC and one PRIVATE finished match, 1 goal each:
                                            // stranger → goals 1, matches.length() 1; anonymous → same
@Test void memberSeesSharedPrivateMatch()   // ivan whose player is in the private lineup → goals 2, matches 2
@Test void ownProfileSeesAll()              // ana viewing her own player → everything she played in
@Test void matchRowsCarryPerMatchNumbers()  // $.matches[0].goals 2, $.matches[0].side "HOME", newest first
@Test void canInviteOnlyForCreatorOfGuest() // creator → canInvite true; other user → false; own player → false
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest=PlayerStatsTest`. Expected: FAIL.
- [ ] **Step 3: Implement `PlayerStatsQuery.totals`** as one query over the player's visible finished matches:

```sql
WITH pm AS (
  SELECT l.match_id, l.side, l.in_goal FROM match_lineup l JOIN sport_match m ON m.id = l.match_id
  WHERE l.player_id = UUID_TO_BIN(:playerId) AND m.status = 'FINISHED' AND m.id IN (/* VISIBLE_MATCH_IDS */)
)
SELECT
  (SELECT COUNT(*) FROM pm) AS appearances,
  (SELECT COUNT(*) FROM match_event e JOIN pm ON pm.match_id = e.match_id WHERE e.type = 'GOAL' AND e.player_id = UUID_TO_BIN(:playerId)) AS goals,
  -- assists: e.type = 'GOAL' AND e.assist_player_id = player; own goals / yellow / red / motm: same shape with player_id
  (SELECT COUNT(*) FROM pm WHERE pm.in_goal AND NOT EXISTS (
     SELECT 1 FROM match_event e WHERE e.match_id = pm.match_id
       AND ((e.type = 'GOAL' AND e.side <> pm.side) OR (e.type = 'OWN_GOAL' AND e.side = pm.side)))) AS clean_sheets
```

  - `matches` reuses `pm`, then calls `MatchViews.summaries` and adds per-match goal and assist counts.
  - "Own profile sees all" needs no special case: your own player is in the lineup of every match you played, so you're a member of all of them.
- [ ] **Step 4:** Run `./mvnw -q test`. Expected: all PASS.
- [ ] **Step 5:** Commit: `feat: visibility-filtered player profiles`.

### Task 2: Claim invites and merge

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__player_claims.sql`
- Create: `backend/src/main/java/com/sportsapp/player/{PlayerClaim,PlayerClaimRepository,ClaimService,ClaimController}.java`
- Modify: `PlayerDtos.java`
- Test: `backend/src/test/java/com/sportsapp/player/ClaimApiTest.java`

**Interfaces:**
- Consumes: `Tokens`, `RateLimiter`, `PlayerService`, `Clock`.
- Produces DTOs:
  - `ClaimInviteResponse(String token, Instant expiresAt)`
  - `ClaimPreviewResponse(UUID playerId, String playerName, int appearances, int goals)`: counts **all** finished matches, because the token holder is the intended owner
  - `ClaimResultResponse(UUID playerId)`: the claiming user's own player
- Produces endpoints:
  - `POST /api/players/{id}/claims` → 201 `ClaimInviteResponse`
  - `GET /api/players/claims/{token}` → 200 `ClaimPreviewResponse` (anonymous allowed)
  - `POST /api/players/claims/{token}` → 200 `ClaimResultResponse` (authenticated; rate-limited as action `claim`)

- [ ] **Step 1: Write `V4__player_claims.sql`.**

```sql
CREATE TABLE player_claim (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  player_id BINARY(16) NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE, expires_at DATETIME(6) NOT NULL,
  FOREIGN KEY (player_id) REFERENCES player(id) ON DELETE CASCADE
);
```

  - There's no `used_at` column: a successful claim deletes the guest, and the cascade removes its claims.

- [ ] **Step 2: Write the failing tests** (`ClaimApiTest`):

```java
@Test void creatorCreatesInvite()               // → 201 $.token (43 chars, base64url), $.expiresAt ≈ now + 14 days; DB stores sha256Hex(token), never the raw token
@Test void onlyCreatorOfUnclaimedGuest()        // other user → 403; own player or already-claimed player → 409 CLAIM_INVALID
@Test void newInviteSupersedesOld()             // invite t1, invite t2 → preview t1 → 409 CLAIM_INVALID; t2 → 200
@Test void previewCountsAllFinishedMatches()    // guest in 1 private + 1 public finished match, 3 goals → anonymous preview appearances 2, goals 3
@Test void claimMergesHistory()                 // guest G (2 matches, 1 goal, 1 assist, on team T) claimed by ivan →
                                                // G deleted; ivan's player has appearances 2, goals 1, assists 1; ivan's player on T's roster;
                                                // ivan can now GET the private match (member)
@Test void claimDeduplicatesRoster()            // ivan's player already on T and G on T → one team_player row for T
@Test void mergeConflictWhenBothInSameMatch()   // ivan's player and G in the same lineup → 409 MERGE_CONFLICT; nothing changed; token still valid
@Test void tokenSingleUse()                     // second claim with same token → 409 CLAIM_INVALID
@Test void expiredToken()                       // set expires_at in the past via jdbc → preview and claim → 409 CLAIM_INVALID
@Test void claimingAPlayerThatIsAlreadyYours()  // Review Focus 5: a claim row pointing at a linked player (inserted via jdbc) → 409 CLAIM_INVALID
@Test void claimRequiresAuth()                  // POST without token → 401
```

- [ ] **Step 3:** Run `./mvnw -q test -Dtest=ClaimApiTest`. Expected: FAIL.
- [ ] **Step 4: Implement `ClaimService.claim(UUID userId, String token)`** as `@Transactional`:
  1. Load the claim by `sha256Hex(token)` with `@Lock(PESSIMISTIC_WRITE)`, **before any other read in the transaction** (M3 lesson: at REPEATABLE READ the first plain read fixes the snapshot, so a claim that waited on the lock would otherwise validate against stale rows). If it's missing or expired → `CLAIM_INVALID`.
  2. `guest = player(claim.playerId)`. If `guest.userId != null`, or the guest is the user's own player → `CLAIM_INVALID`.
  3. `mine = ownPlayer(userId)`. If any match lineup contains both `mine` and `guest` → `MERGE_CONFLICT`.
  4. Run these `JdbcClient` updates:
     - `UPDATE match_lineup SET player_id = mine WHERE player_id = guest`
     - `UPDATE match_event SET player_id = mine WHERE player_id = guest`
     - `UPDATE match_event SET assist_player_id = mine WHERE assist_player_id = guest`
     - `DELETE tp FROM team_player tp JOIN team_player t2 ON t2.team_id = tp.team_id AND t2.player_id = mine WHERE tp.player_id = guest`
     - `UPDATE team_player SET player_id = mine WHERE player_id = guest`
     - `DELETE FROM player WHERE id = guest` (the cascade removes claims)
  - Invite creation deletes the guest's existing claims, then inserts the new one.
- [ ] **Step 5:** Run `./mvnw -q test`. Expected: all PASS.
- [ ] **Step 6:** Commit: `feat: guest claim invites and merge`.

### Task 3: Frontend profile, invite and claim

**Files:**
- Create: `frontend/src/pages/players/PlayerProfilePage.tsx`, `frontend/src/pages/claim/ClaimPage.tsx`, `frontend/src/components/InviteButton.tsx`
- Modify: `App.tsx`, `PlayersPage.tsx` (names link to profiles), `MatchPage.tsx` (lineup names link to profiles), both locale files, `api/schema.d.ts`

**Interfaces:**
- Consumes: the Task 1 and Task 2 endpoints, `safeNext` (M1), `MatchList` (M3).
- Produces routes `/players/:id` and `/claim/:token`, and query key `['player', id]`.

- [ ] **Step 1:** Run `npm run gen:api`.
- [ ] **Step 2: `PlayerProfilePage`.**
  - Name, plus a "guest" badge when unclaimed.
  - A grid of totals: appearances, goals, assists, clean sheets, yellows, reds, MOTM, own goals.
  - Match history: `MatchList` rows with that match's goals and assists.
  - Rename when `canEdit`, and `InviteButton` when `canInvite`.
  - Renaming your own player also updates the header's name (deferred from M2: it stayed stale until reload). `AuthProvider` gains `refreshMe()`, which re-reads `GET /api/me`; call it after renaming your own player here and on `/players`.
  - `MatchList` gains an optional `extra?: (m: MatchSummary) => ReactNode` rendered on each card, used here for the per-match goals and assists.
- [ ] **Step 3: `InviteButton`.**
  - Posts an invite and shows a modal with the link `${origin}/claim/${token}`, a Copy button (`navigator.clipboard.writeText`) and the expiry date.
  - Includes the hint `claim.shareHint`: "Send this link to the player; it works once and expires in 14 days."
- [ ] **Step 4: `ClaimPage`.**
  - Loads the preview: "N matches, M goals will be added to your profile".
  - Anonymous viewers get Log in and Register buttons linking to `/login?next=/claim/<token>`.
  - Logged-in viewers get a Confirm button that posts the claim, then navigates to `/players/<own playerId>` with a success toast.
  - `CLAIM_INVALID` and `MERGE_CONFLICT` show translated messages.
- [ ] **Step 5:** Add locale keys `profile.*`, `claim.*`, `errors.CLAIM_INVALID` and `errors.MERGE_CONFLICT`. Then run `npm test && npm run build`. Expected: PASS.
- [ ] **Step 6: Verify manually** with two browsers:
  1. User A creates guest "Marko", plays and finishes a private match with him, and copies the invite.
  2. User B opens the link while logged out, registers, gets redirected back and confirms.
  3. B's profile shows the goals, and B can open the private match.
- [ ] **Step 7:** Commit: `feat: player profiles, invites and claim flow`.
