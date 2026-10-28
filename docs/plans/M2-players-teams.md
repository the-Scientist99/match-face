# M2 — Players, Teams, Admin Grants Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Organizers create guest players and teams with rosters. Ownership is recorded as admin grants, and every access check goes through `AccessService`.

**Architecture:**
- The `access` module owns `admin_grant` and the read/admin decision for each resource type. It grows in M3 (matches) and M5 (competitions).
- "My players" is one SQL query in `PlayerService`, reused by rosters (M2) and lineups (M3).

**Tech Stack, Global Constraints, conventions:** [00-main-plan.md](00-main-plan.md)

**Spec:** `docs/superpowers/specs/2026-10-28-sports-league-app-design.md` (sections 4 People/Teams/Permissions, 6)

---

### Task 1: Players API and "my players"

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__teams_grants.sql`
- Modify: `backend/src/main/java/com/sportsapp/player/PlayerService.java`
- Create: `backend/src/main/java/com/sportsapp/player/{PlayerController,PlayerDtos}.java`
- Modify: `backend/src/test/java/com/sportsapp/common/IntegrationTest.java` (helper `createGuest`)
- Test: `backend/src/test/java/com/sportsapp/player/PlayerApiTest.java`

**Interfaces:**
- Consumes (from M1): `Player`, `PlayerRepository`, `PlayerService.createOwn`, `Auth`.
- Produces DTOs:
  - `CreatePlayerRequest(@NotBlank @Size(max=50) name)`
  - `RenamePlayerRequest(@NotBlank @Size(max=50) name)`
  - `PlayerResponse(UUID id, String name, boolean claimed, boolean canEdit)`
- Produces endpoints:
  - `POST /api/players` → 201
  - `PATCH /api/players/{id}` → 200
  - `GET /api/players/mine` → `List<PlayerResponse>`, own player first, then the rest by name
  - `GET /api/players/{id}` → `PlayerResponse` (M4 replaces it with the profile)
- Produces in `PlayerService`:
  - `createGuest(UUID creatorId, String name): Player`
  - `myPlayerIds(UUID userId): Set<UUID>`: own player ∪ players created by me ∪ players on rosters of teams I admin
  - `canEdit(UUID userId, Player p): boolean`: (`p.userId == null && p.createdByUserId == userId`) || `p.userId == userId`
  - `getRequired(UUID id): Player` (404 if missing)
  - `namesById(Collection<UUID>): Map<UUID,String>`
- Produces helper `IntegrationTest.createGuest(TestUser owner, String name): UUID`.

- [ ] **Step 1: Write `V2__teams_grants.sql`.** It's written now because "my players" reads `team_player` and `admin_grant`.

```sql
CREATE TABLE admin_grant (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  user_id BINARY(16) NOT NULL, resource_type VARCHAR(20) NOT NULL, resource_id BINARY(16) NOT NULL, role VARCHAR(20) NOT NULL,
  UNIQUE (user_id, resource_type, resource_id), INDEX (resource_type, resource_id),
  FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);
CREATE TABLE team (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL, name VARCHAR(50) NOT NULL, sport VARCHAR(20) NOT NULL
);
CREATE TABLE team_player (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  team_id BINARY(16) NOT NULL, player_id BINARY(16) NOT NULL,
  UNIQUE (team_id, player_id), INDEX (player_id),
  FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES player(id) ON DELETE CASCADE
);
```

- [ ] **Step 2: Write the failing tests** (`PlayerApiTest`):

```java
@Test void createGuestTrimsName()                 // POST {"name":"  Marko "} → 201 $.name "Marko", $.claimed false, $.canEdit true
@Test void createGuestRequiresAuth()              // no token → 401
@Test void blankName()                            // {"name":"  "} → 400 VALIDATION, $.errors.name "NotBlank"
@Test void creatorRenamesGuest()                  // PATCH → 200 $.name "Marko P"
@Test void othersCannotRenameGuest()              // other user PATCH → 403 FORBIDDEN (players are readable by anyone)
@Test void userRenamesOwnPlayerButCreatorCannot() // ana PATCH own player → 200
@Test void mineListsOwnFirstThenCreatedByName()   // ana creates "Zed", "Bo" → names ["Ana", "Bo", "Zed"]; ivan's guests absent
@Test void unknownPlayer()                        // GET /api/players/{random v7} → 404
```

- [ ] **Step 3:** Run `./mvnw -q test -Dtest=PlayerApiTest`. Expected: FAIL.
- [ ] **Step 4: Implement.** `myPlayerIds` is a single `JdbcClient` query:

```sql
SELECT BIN_TO_UUID(p.id) FROM player p WHERE p.user_id = UUID_TO_BIN(:u) OR (p.created_by_user_id = UUID_TO_BIN(:u) AND p.user_id IS NULL)
UNION
SELECT BIN_TO_UUID(tp.player_id) FROM team_player tp
JOIN admin_grant g ON g.resource_type = 'TEAM' AND g.resource_id = tp.team_id AND g.user_id = UUID_TO_BIN(:u)
```

  - `mine` loads those ids and sorts them: own player first, then by name (case-insensitive).
- [ ] **Step 5:** Run `./mvnw -q test`. Expected: all PASS.
- [ ] **Step 6:** Commit: `feat: guest players and "my players"`.

### Task 2: Access module and teams API

**Files:**
- Create: `backend/src/main/java/com/sportsapp/access/{AdminGrant,AdminGrantRepository,ResourceType,AccessService}.java`
- Create: `backend/src/main/java/com/sportsapp/team/{Team,TeamPlayer,TeamRepository,TeamPlayerRepository,TeamService,TeamController,TeamDtos}.java`
- Create: `backend/src/main/java/com/sportsapp/sport/Sport.java` (`enum Sport { FOOTBALL }`)
- Modify: `IntegrationTest` (helper `createTeam`)
- Test: `backend/src/test/java/com/sportsapp/team/TeamApiTest.java`

**Interfaces:**
- Consumes: `PlayerService.myPlayerIds`, `PlayerService.namesById`.
- Produces `AccessService`, with the exact signatures from the main plan. In this milestone `TEAM` is complete and `MATCH`/`COMPETITION` don't exist yet.
  - `canAdmin(u, TEAM, t)`: an `admin_grant` row exists.
  - `canRead(v, TEAM, t)`: `canAdmin`, **or** the viewer's own player is on the roster. M5 adds "or the team plays in a competition the viewer can read".
  - Calling with `MATCH` or `COMPETITION` before M3/M5 throws `IllegalArgumentException`.
- Produces DTOs:
  - `CreateTeamRequest(@NotBlank @Size(max=50) name)`
  - `RenameTeamRequest(same)`
  - `SetRosterRequest(@NotNull List<UUID> playerIds)`
  - `TeamResponse(UUID id, String name, Sport sport, List<RosterPlayer> players, boolean canAdmin)`, where `RosterPlayer(UUID id, String name, boolean claimed)` is sorted by name
  - `TeamSummary(UUID id, String name)`
- Produces endpoints:
  - `POST /api/teams` → 201, and the creator gets a `TEAM` `OWNER` grant
  - `GET /api/teams/{id}` (`requireRead`)
  - `PATCH /api/teams/{id}` (`requireAdmin`)
  - `PUT /api/teams/{id}/players` (`requireAdmin`; replaces the roster)
  - `GET /api/teams/mine` → `List<TeamSummary>` (admin or roster member, by name)
- Produces in `TeamService`:
  - `rosterIds(UUID teamId): Set<UUID>`
  - `addToRoster(UUID teamId, UUID playerId)` (idempotent; used by M5)
  - `name(UUID teamId): String`
- Produces helper `IntegrationTest.createTeam(TestUser owner, String name, UUID... playerIds): UUID`.

- [ ] **Step 1: Write the failing tests** (`TeamApiTest`):

```java
@Test void createTeamGrantsOwner()               // POST → 201 $.canAdmin true, $.players empty; admin_grant row (TEAM, OWNER) exists
@Test void setRosterFromMyPlayers()              // PUT [guestA, guestB, ownPlayer] → 200 $.players.length() 3, sorted by name
@Test void rosterRejectsPlayerNotInMyPlayers()   // PUT [ivansGuest] → 400 $.code "PLAYER_NOT_AVAILABLE"; roster unchanged
@Test void rosterKeepsExistingMembers()          // existing roster player created by someone else stays allowed (team admin ⇒ in my players)
@Test void rosterMemberCanReadNotAdmin()         // ivan's own player on ana's roster: GET → 200 $.canAdmin false; PATCH → 403
                                                 // setup: insert the team_player row with jdbc; only a claim (M4) puts a registered user's player on someone's roster
@Test void strangerAndAnonymousGet404()          // other user GET → 404; no token GET → 404
@Test void strangerPatchIs404Not403()            // PATCH by stranger → 404
@Test void mineListsAdminAndMemberTeams()        // ivan sees ana's team (member) and his own team; not unrelated teams
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest=TeamApiTest`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - The roster check: every requested id must be in `myPlayerIds(admin)`, otherwise `400 PLAYER_NOT_AVAILABLE`.
  - The roster write deletes and re-inserts in one transaction.
- [ ] **Step 4:** Run `./mvnw -q test`. Expected: all PASS.
- [ ] **Step 5:** Commit: `feat: teams, rosters and access service`.

### Task 3: Frontend players and teams

**Files:**
- Create: `frontend/src/pages/players/PlayersPage.tsx`, `frontend/src/pages/teams/{TeamsPage,TeamPage}.tsx`, `frontend/src/components/PlayerPicker.tsx`
- Modify: `frontend/src/App.tsx`, `frontend/src/components/Layout.tsx` (nav), both locale files, `frontend/src/api/schema.d.ts`

**Interfaces:**
- Consumes: the Task 1 and Task 2 endpoints.
- Produces query keys `['players','mine']`, `['teams','mine']` and `['team', id]`.
- Produces `PlayerPicker({ value: string[], onChange, excludeIds?: string[] })`, reused for lineups in M3.
  - It's a Mantine `MultiSelect` over `GET /api/players/mine`, searchable.
  - Its last option, "+ Add guest '<search>'", creates a guest with `POST /api/players` and selects it.

- [ ] **Step 1:** Run `npm run gen:api` with the backend running.
- [ ] **Step 2: `PlayersPage` (`/players`).**
  - Shows my players as a list with a "guest" badge when `!claimed`.
  - Has an add-guest form, and an inline rename when `canEdit`.
  - Invalidates `['players','mine']` after mutations.
- [ ] **Step 3: `TeamsPage` (`/teams`) and `TeamPage` (`/teams/:id`).**
  - `TeamsPage` lists my teams and has a create form; after creating, it navigates to the team.
  - `TeamPage` shows the roster. Admins also get a rename field and a roster editor (`PlayerPicker` + Save → `PUT`).
  - API errors go through `useNotifyError`.
- [ ] **Step 4:** Add nav links Players and Teams, plus locale keys `players.*`, `teams.*`, `errors.PLAYER_NOT_AVAILABLE`.
- [ ] **Step 5:** Run `npm test && npm run build`. Expected: PASS (the locale parity test catches missing keys).
- [ ] **Step 6: Verify manually.**
  1. Create a guest from `PlayerPicker` inside the roster editor.
  2. Save the roster.
  3. Log in as a second user who isn't on the roster and open the team URL; it shows not found.
- [ ] **Step 7:** Commit: `feat: players and teams screens`.
