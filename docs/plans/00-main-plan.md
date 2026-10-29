# Sports League App — Main Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a responsive web app where casual football groups create matches, track player stats, and run leagues and cups. Montenegrin + English.

**Architecture:**
- **Backend:** one Spring Boot modular monolith exposing a REST API under `/api`.
- **Frontend:** a React SPA that talks to that API. A native mobile app will reuse the API later.
- **Data:** match data is an event log (goals, cards, …). Scores, standings and stats are always calculated from it with SQL, never stored.

**Tech Stack:** see Global Constraints below.

**Spec:** `docs/superpowers/specs/2026-10-28-sports-league-app-design.md`. Read it before any milestone; this plan argues from it.

## How these plans are organised

- This file holds the decisions every milestone depends on: constraints, shared interfaces and conventions, review focus. It also has the milestone index, and M7 inline.
- Each larger milestone has its own plan file (table below).
- **Before starting a milestone:** re-read its plan and update it to match the code that actually exists, because earlier milestones may have changed names. Commit that update first.

| # | Plan | Outcome | Depends on |
|---|---|---|---|
| M0 | [M0-foundation.md](M0-foundation.md) | Backend and frontend skeletons, local MySQL, error format, CI | — |
| M1 | [M1-accounts-auth.md](M1-accounts-auth.md) | Register, login, refresh, logout, language | M0 |
| M2 | [M2-players-teams.md](M2-players-teams.md) | Guest players, teams, rosters, admin grants | M1 |
| M3 | [M3-matches.md](M3-matches.md) | One-off matches, lineups, events, result screen: **first real use** | M2 |
| M4 | [M4-profiles-claims.md](M4-profiles-claims.md) | Player profiles with visibility-filtered stats; claim and merge | M3 |
| M5 | [M5-leagues.md](M5-leagues.md) | Leagues: fixtures, standings, competition stats | M4 |
| M6 | [M6-cups.md](M6-cups.md) | Cups: bracket, byes, advancement, penalties | M5 |
| M7 | inline below | Public share pages, end-to-end test, single deployable, deployment | M6 |
| M8 | [M8-images.md](M8-images.md) | Player photos, team logos, competition photos (stored on disk) | M6 (can ship before M7) |

## Global Constraints

Every task's requirements implicitly include this section.

**Toolchain**
- Java 25 (Corretto, installed). Spring Boot **4.1.2**, which brings Hibernate 7.4 and Flyway 12. Use the Maven **wrapper** (`./mvnw`), because Maven is not installed globally.
- MySQL 26.7, installed natively at `/usr/local/mysql`. **No Docker.**
  - Dev database `sportsapp`, test database `sportsapp_test`, user `sportsapp`/`sportsapp` (dev only).
  - CI uses the service image `mysql:26.7`.
- Node 24. Vite 8, React 19, TypeScript, Mantine 9, React Router 8 (`react-router` package), TanStack Query 5, i18next 26 + react-i18next 17, openapi-typescript 7 + openapi-fetch 0.17, Vitest 5, Playwright 1.64.
- Root Java package: `com.sportsapp`.

**Persistence**
- Every table has `id BINARY(16)` and `created_at DATETIME(6)`.
- Entities extend `BaseEntity`, which uses `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)`. Ids are serialized as the standard 36-character string in JSON.
- In raw SQL, write UUIDs as `UUID_TO_BIN(:param)` and read them as `BIN_TO_UUID(col)`, **never with the swap flag**. Bind UUID parameters as `uuid.toString()`.
- Time is `Instant` in Java and `DATETIME(6)` in UTC in MySQL (`hibernate.jdbc.time_zone: UTC`).
- Entities have **no JPA associations**. Foreign keys are plain `UUID` fields.
- The schema changes only through Flyway `backend/src/main/resources/db/migration/V<n>__<name>.sql`, with `spring.jpa.hibernate.ddl-auto: none`. One migration per milestone, numbered V1…V5 in milestone order. Never edit a migration after its milestone is merged.
- The match table is `sport_match`, because `MATCH` is reserved in MySQL. Its child tables are `match_lineup` and `match_event`.
- Enums are stored as `VARCHAR` with `@Enumerated(EnumType.STRING)`.

**Errors and access**
- Errors are Spring `ProblemDetail` with a `code` property, thrown as `ApiException`. The backend never returns user-facing text.
- A resource the viewer cannot read → `404`. Readable but not admin → `403`. Unauthenticated on a protected action → `401`.
- All `GET /api/**` endpoints accept anonymous requests. Visibility rules decide what they return.

**Domain rules**
- Points: win 3, draw 1, loss 0.
- Tiebreakers: points → goal difference → goals scored → head-to-head points → team name.
- Event minute: 0–150 or absent.

**Security**
- Access JWT lasts 15 minutes, HS256, `sub` = user id, `iss` = `sportsapp`.
- Refresh token: 32 random bytes, 30 days, stored as SHA-256 hex and rotated on every use. Cookie `refresh_token`: `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`.
- Claim token: 32 random bytes, stored as SHA-256 hex, 14 days, single use.
- Rate limit: 10 requests per 60 seconds per (action, client IP) for `login`, `register` and `claim`.

**Frontend**
- Every user-visible string is an i18next key that exists in **both** `frontend/src/locales/me.json` and `en.json` (enforced by a test from M0).
- The default language is the stored choice, else `en` if the browser language starts with `en`, else `me`.

## Shared conventions and interfaces

Milestone plans reference these by name. Where a milestone extends one, its plan says so.

### Backend layout
```
backend/src/main/java/com/sportsapp/
  common/    BaseEntity, ApiException, ApiExceptionHandler, Auth, RateLimiter, Tokens, Visibility
  account/   user_account, refresh_token, auth + /api/me            (M1)
  access/    admin_grant, AccessService, ResourceType, VisibilitySql (M2, extended in M3/M5)
  player/    player, player_claim, players API                       (M1 entity, M2 API, M4 claims)
  team/      team, team_player                                       (M2)
  sport/     Sport, SportRules, FootballRules                        (M3)
  match/     sport_match, match_lineup, match_event, matches API     (M3, extended in M5/M6)
  stats/     read-only stats SQL                                     (M4, M5)
  competition/ competition, competition_team, stage, generators      (M5, M6)
```
- Each module has its own entity, repository, service, controller, and one `<Module>Dtos.java` holding request/response `record`s.
- Writes go through the owning module's service. Read-only `JdbcClient` SQL may join across modules' tables.

### `common` (M0/M1)
- `abstract class BaseEntity`: `UUID getId()`, `Instant getCreatedAt()`.
- `class ApiException extends RuntimeException`: `HttpStatus status()`, `String code()`. Static factories:
  - `notFound()` → 404 `NOT_FOUND`
  - `forbidden()` → 403 `FORBIDDEN`
  - `unauthorized()` → 401 `UNAUTHORIZED`
  - `badRequest(String code)` → 400
  - `conflict(String code)` → 409
  - `tooManyRequests()` → 429 `RATE_LIMITED`
- `ApiExceptionHandler` (`@RestControllerAdvice`): maps `ApiException`, bean validation (400 `VALIDATION`, plus `errors` map of field → constraint simple name such as `"NotBlank"`), and `ObjectOptimisticLockingFailureException` (409 `STALE`).
- `final class Auth`:
  - `static UUID userId(Jwt jwt)` throws `unauthorized()` when `jwt == null`.
  - `static UUID userIdOrNull(Jwt jwt)`.
  - Controllers take `@AuthenticationPrincipal Jwt jwt`.
- `class RateLimiter` (`@Component`): `void check(String action, String clientKey)`, `void reset()` (tests only).
- `final class Tokens`: `static String newToken()` (32 random bytes, base64url, no padding), `static String sha256Hex(String)`.
- `enum Visibility { PUBLIC, PRIVATE }`.

### `access` (M2, extended in M3 and M5)
```java
public enum ResourceType { COMPETITION, MATCH, TEAM }
public class AccessService {
  void grantOwner(UUID userId, ResourceType type, UUID resourceId);
  boolean canAdmin(@Nullable UUID userId, ResourceType type, UUID resourceId);
  boolean canRead(@Nullable UUID viewerId, ResourceType type, UUID resourceId);
  void requireAdmin(@Nullable UUID userId, ResourceType type, UUID resourceId); // !canRead → 404, !canAdmin → 403
  void requireRead(@Nullable UUID viewerId, ResourceType type, UUID resourceId);  // !canRead → 404
}
public final class VisibilitySql { public static final String VISIBLE_MATCH_IDS; } // subquery selecting sport_match.id visible to :viewerId
```
- `canAdmin` implies `canRead`. A non-existent id is never readable, so it always gives 404.

### Test conventions
- JUnit 5 + AssertJ + MockMvc with `jsonPath`.
- Integration tests extend `common/IntegrationTest` (in `src/test`):
  - `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`.
  - It truncates every table except `flyway_schema_history` before each test, and **refuses to run unless the database name ends in `_test`**.
  - Helper methods are added per milestone (see each plan's Interfaces).
- Pure logic (generators, standings, rules) gets plain unit tests with no Spring.
- Run backend tests: `cd backend && ./mvnw -q test`. Run one class: `./mvnw -q test -Dtest=ClassName`.

### Frontend layout
```
frontend/src/
  main.tsx  App.tsx (route table)  i18n.ts  locales/{me,en}.json
  api/      schema.d.ts (generated), client.ts, errors.ts
  auth/     AuthProvider.tsx, RequireAuth.tsx
  components/ Layout.tsx and shared widgets
  pages/<area>/<Name>Page.tsx
```
- Data access uses `useQuery`/`useMutation` with the typed `api` client. After a mutation, invalidate the affected query keys (listed per milestone).
- Regenerate API types after backend changes: run the backend, then `npm run gen:api`, and commit `src/api/schema.d.ts`.
- i18n keys use the form `<area>.<name>`. Error codes map to `errors.<CODE>`; validation constraint names map to `validation.<Constraint>`.

## Review Focus

These are inputs the spec implies but no happy-path test would hit. Each has a pinned test in the owning task.

1. **Email case and whitespace.** Registering `" Ana@Mail.com "` stores `ana@mail.com`. Logging in with `ANA@mail.com` works. Registering `ana@MAIL.com` again gives `409 EMAIL_TAKEN`. Owner: M1 Task 2.
2. **Removing or moving a lineup player who has events** (as scorer, assister, carded player or man of the match) → `409 PLAYER_HAS_EVENTS`, and the lineup is unchanged. Owner: M3 Task 3.
3. **The same team on both sides** of a one-off match → `400 SAME_TEAM`. Owner: M3 Task 2.
4. **A goal with no known scorer** still counts in the score and standings, but in no player's goals, and it does break the other keeper's clean sheet. Owner: M3 Task 3 (`unknownScorerGoalCounts`) and M4 Task 1 (`unknownScorerGoalCreditsNobody`, `cleanSheetRules`).
5. **Claiming a player that already belongs to a user**, including your own, → `409 CLAIM_INVALID`, and nothing changes. Owner: M4 Task 2.

---

## M7 — Public pages, end-to-end test, single deployable

**Before M7:** ask the user where to deploy (a VPS with systemd, Railway, Fly.io, etc.). The jar and MySQL setup below works on all of them; only Task 3 Step 5 depends on the choice.

### Task 7.1: Anonymous share pages

**Files:**
- Modify: `frontend/src/App.tsx`, `frontend/src/components/Layout.tsx`, `frontend/src/pages/competitions/CompetitionPage.tsx`, `frontend/src/pages/matches/MatchPage.tsx`, `frontend/src/pages/players/PlayerProfilePage.tsx`, both locale files
- Create: `frontend/src/components/ShareButton.tsx`

**Interfaces:**
- Consumes: the anonymous `GET` endpoints from M3–M6 (already enforced and tested on the backend).
- Produces: routes `/c/:id` and `/m/:id`, aliases of `/competitions/:id` and `/matches/:id` that work **without** `RequireAuth`, plus `/players/:id` without `RequireAuth`.

- [ ] **Step 1:** Move the three detail routes outside `RequireAuth`.
  - Each page hides admin controls when `canAdmin` is false.
  - When the viewer is anonymous, `Layout` shows a "Log in" button instead of the user menu.
  - A 404 from the API renders `errors.NOT_FOUND`, with a link to log in.
- [ ] **Step 2:** Add `ShareButton({ path }: { path: string })`. It copies `window.location.origin + path` with `navigator.clipboard.writeText` and shows the toast `share.copied`. Show it on competition and one-off match pages when `visibility === 'PUBLIC'`.
- [ ] **Step 3: Verify manually.** In a private browser window, open a public competition's `/c/<id>` (standings visible) and a private one (not-found message).
- [ ] **Step 4:** Run `npm test && npm run build`. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: anonymous share pages`.

### Task 7.2: End-to-end test

**Files:**
- Create: `frontend/playwright.config.ts`, `frontend/e2e/league.spec.ts`
- Modify: `frontend/package.json` (script `e2e`)

- [ ] **Step 1:** Install `@playwright/test@1.64` and run `npx playwright install chromium`. Configure `webServer` with two entries:
  - backend: `cd ../backend && ./mvnw -q spring-boot:run`, url `http://localhost:8080/v3/api-docs`, timeout 180 s, `reuseExistingServer: true`;
  - frontend: `npm run dev`, url `http://localhost:5173`, `reuseExistingServer: true`.
- [ ] **Step 2:** Write `league.spec.ts`. Use a unique email per run (`e2e-${Date.now()}@test.dev`) so it can run against the dev database.
  1. Register.
  2. Create a private league "E2E League".
  3. Create teams "Alpha" (guest "Marko") and "Beta" (guest "Ivan") inline.
  4. Generate fixtures.
  5. Open the round-1 match and add both players to the lineup.
  6. Goal for Marko, then Finish.
  7. Open the Table tab.
  - Expect the Alpha row to show `P 1`, `W 1`, `Pts 3` and to come first.
  - Expect the Stats tab to list "Marko" with `1` under top scorers.
- [ ] **Step 3:** Run `npm run e2e`. Expected: 1 passed.
- [ ] **Step 4:** Commit: `test: league end-to-end flow`.

### Task 7.3: Single deployable jar

**Files:**
- Create: `backend/src/main/java/com/sportsapp/common/SpaForwardController.java`, `backend/src/main/resources/application-prod.yml`, `scripts/build.sh`
- Test: `backend/src/test/java/com/sportsapp/common/SpaForwardControllerTest.java`

- [ ] **Step 1: Write the failing test.**

```java
@Test void forwardsClientRoutesToIndex() throws Exception {
  mvc.perform(get("/c/0190a3b4-0000-7000-8000-000000000000")).andExpect(forwardedUrl("/index.html"));
  mvc.perform(get("/claim/abc")).andExpect(forwardedUrl("/index.html"));
}
@Test void doesNotForwardApiOrFiles() throws Exception {
  mvc.perform(get("/api/nope")).andExpect(status().isNotFound());
  mvc.perform(get("/assets/app.js")).andExpect(forwardedUrl(null));
}
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest=SpaForwardControllerTest`. Expected: FAIL.
- [ ] **Step 3: Implement `SpaForwardController`.** It forwards `GET` requests to `/index.html` when the path doesn't start with `/api/` and its **last** segment contains no dot. A path pattern with `/**` can't express "last segment", so check the path in code. Then add the prod config:
  - `application-prod.yml`: `app.jwt.secret: ${JWT_SECRET}` (no default, so startup fails without it), `app.auth.cookie-secure: true`, `server.forward-headers-strategy: framework` (so the rate limiter sees the real client IP behind a proxy), datasource from `DB_URL`/`DB_USER`/`DB_PASSWORD`.
  - `scripts/build.sh`: `cd frontend && npm ci && npm run build`, then copy `frontend/dist/*` into `backend/src/main/resources/static/` (git-ignored), then `cd backend && ./mvnw -q -DskipTests package`.
- [ ] **Step 4: Verify.**
  - Run `./mvnw -q test`. Expected: all PASS.
  - Run `scripts/build.sh && SPRING_PROFILES_ACTIVE=prod JWT_SECRET=$(openssl rand -base64 48) java -jar backend/target/backend-*.jar`, then open `http://localhost:8080/c/<id>`. Expected: the SPA loads.
- [ ] **Step 5:** Deploy to the target the user chose (jar + MySQL 26.7 + HTTPS reverse proxy). Write the steps into `README.md` under "Deployment".
  - Images (M8): set `IMAGES_DIR=/var/lib/sportsapp/images`, outside the jar's folder and owned by the app user. Lightsail's automatic snapshots back it up together with the database.
  - The reverse proxy must allow request bodies of at least 5 MB (nginx: `client_max_body_size 6m;`), or uploads fail before reaching the app.
- [ ] **Step 6:** Commit: `feat: single deployable jar and prod profile`.
