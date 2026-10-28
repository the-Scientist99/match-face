# M1 — Accounts and Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can register, log in, stay logged in across reloads, log out, and choose their language. Every user gets their own player.

**Architecture:**
- Spring Security resource server validates the HS256 access JWTs.
- A rotating refresh token sits in an httpOnly cookie.
- The SPA keeps the access token in memory and refreshes it transparently on a 401.

**Tech Stack, Global Constraints, conventions:** [00-main-plan.md](00-main-plan.md)

**Spec:** `docs/superpowers/specs/2026-10-28-sports-league-app-design.md` (sections 4 People, 7 Backend/Auth, 8)

---

### Task 1: Accounts schema, entities, UUID storage check

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__accounts.sql`
- Create: `backend/src/main/java/com/sportsapp/account/{UserAccount,UserAccountRepository,RefreshToken,RefreshTokenRepository}.java`
- Create: `backend/src/main/java/com/sportsapp/player/{Player,PlayerRepository}.java`
- Test: `backend/src/test/java/com/sportsapp/account/UuidStorageTest.java`

**Interfaces:**
- Produces entities (getters and setters for every column):
  - `UserAccount(email, passwordHash, locale)`
  - `RefreshToken(userId, tokenHash, expiresAt, revokedAt)`
  - `Player(name, userId, createdByUserId)`
- Produces repositories:
  - `UserAccountRepository.findByEmail(String): Optional<UserAccount>`
  - `RefreshTokenRepository.findByTokenHash(String): Optional<RefreshToken>`, plus `@Modifying` `revokeAllForUser(UUID userId, Instant now)`
  - `PlayerRepository.findByUserId(UUID): Optional<Player>`

- [ ] **Step 1: Write `V1__accounts.sql`.**

```sql
CREATE TABLE user_account (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL, locale VARCHAR(2) NOT NULL
);
CREATE TABLE refresh_token (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  user_id BINARY(16) NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME(6) NOT NULL, revoked_at DATETIME(6) NULL,
  FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);
CREATE TABLE player (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  name VARCHAR(50) NOT NULL, user_id BINARY(16) NULL UNIQUE, created_by_user_id BINARY(16) NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user_account(id),
  FOREIGN KEY (created_by_user_id) REFERENCES user_account(id)
);
```

  - `user_account` has no display name: the user's name is their player's name.

- [ ] **Step 2: Write the failing test.** `UuidStorageTest extends IntegrationTest`:

```java
@Test void idsAreUuidV7StoredAsStandardBinary() {
  UserAccount u = users.save(new UserAccount("a@test.dev", "x", "en"));
  assertThat(u.getId().version()).isEqualTo(7);
  String viaSql = jdbc.sql("SELECT BIN_TO_UUID(id) FROM user_account WHERE email = 'a@test.dev'").query(String.class).single();
  assertThat(viaSql).isEqualTo(u.getId().toString());
  assertThat(jdbc.sql("SELECT COUNT(*) FROM user_account WHERE id = UUID_TO_BIN(:id)")
      .param("id", u.getId().toString()).query(Integer.class).single()).isEqualTo(1);
  assertThat(users.findById(u.getId())).isPresent();
}
```

- [ ] **Step 3:** Run `./mvnw -q test -Dtest=UuidStorageTest`. Expected: FAIL (compilation).
- [ ] **Step 4:** Implement the entities and repositories. Write `@Table`/`@Column` names explicitly where the Java name differs from the snake_case column.
- [ ] **Step 5:** Run the same test. Expected: PASS.
  - If the `BIN_TO_UUID` assertion fails, Hibernate isn't storing UUIDs as standard binary. Fix the mapping (`hibernate.type.preferred_uuid_jdbc_type`); don't change the test.
- [ ] **Step 6:** Commit: `feat: accounts schema with UUIDv7 ids`.

### Task 2: Auth API

**Files:**
- Create: `backend/src/main/java/com/sportsapp/account/{SecurityConfig,TokenService,AuthService,AuthController,MeController,AccountDtos}.java`
- Create: `backend/src/main/java/com/sportsapp/common/{Auth,RateLimiter,Tokens}.java`
- Create: `backend/src/main/java/com/sportsapp/player/PlayerService.java`
- Modify: `backend/src/main/resources/application.yml` (the `app:` block)
- Modify: `backend/src/test/java/com/sportsapp/common/IntegrationTest.java` (helpers)
- Test: `backend/src/test/java/com/sportsapp/account/AuthApiTest.java`, `backend/src/test/java/com/sportsapp/common/RateLimiterTest.java`

**Interfaces:**
- Consumes: Task 1 entities and repositories.
- Produces DTOs (`AccountDtos`):
  - `RegisterRequest(email, password, displayName, locale)`: `@Email @Size(max=254)`, `@Size(min=8,max=128)`, `@NotBlank @Size(max=50)`, `@Pattern("me|en")`
  - `LoginRequest(email, password)`
  - `PatchMeRequest(@Pattern("me|en") locale)`
  - `MeResponse(UUID id, String email, String locale, UUID playerId, String displayName)`
  - `AuthResponse(String accessToken, MeResponse user)`
- Produces endpoints:
  - `POST /api/auth/register` → 201 `AuthResponse` + cookie
  - `POST /api/auth/login` → 200 `AuthResponse` + cookie
  - `POST /api/auth/refresh` → 200 `AuthResponse` + new cookie
  - `POST /api/auth/logout` → 204 + cleared cookie
  - `GET /api/me` → `MeResponse`
  - `PATCH /api/me` → `MeResponse`
- Produces services:
  - `PlayerService.createOwn(UUID userId, String name): Player` (M2 adds more methods)
  - `PlayerService.ownPlayer(UUID userId): Player`
  - `common.Auth`, `common.RateLimiter`, `common.Tokens` (as in the main plan)
- Produces `IntegrationTest` helpers:
  - `record TestUser(UUID id, UUID playerId, String token)`
  - `TestUser register(String name)`: email `name.toLowerCase() + "@test.dev"`, password `password123`, locale `en`
  - `RequestPostProcessor as(TestUser u)`: sets `Authorization: Bearer …`
  - `String toJson(Object body)`
  - `@BeforeEach` calls `rateLimiter.reset()`

- [ ] **Step 1: Write the failing tests.**

`RateLimiterTest` (unit test with a mutable `Clock`):
```java
@Test void allowsTenPerMinutePerActionAndKeyThenRejects() {
  for (int i = 0; i < 10; i++) limiter.check("login", "1.2.3.4");
  assertThatThrownBy(() -> limiter.check("login", "1.2.3.4")).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RATE_LIMITED"));
  limiter.check("login", "5.6.7.8");       // other key unaffected
  limiter.check("register", "1.2.3.4");    // other action unaffected
  clock.advance(Duration.ofSeconds(61));
  limiter.check("login", "1.2.3.4");       // window reset
}
```

`AuthApiTest extends IntegrationTest`:
```java
@Test void registerNormalizesEmailCreatesPlayerAndSetsCookie() // POST " Ana@Mail.com " → 201; $.user.email "ana@mail.com", $.user.displayName "Ana",
   // $.user.playerId not null, $.accessToken not empty; Set-Cookie contains "refresh_token=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth";
   // player row exists with user_id = $.user.id and created_by_user_id = $.user.id
@Test void registerDuplicateEmailIgnoringCase()     // second register "ana@MAIL.com" → 409 $.code "EMAIL_TAKEN"
@Test void registerValidation()                     // email "nope", password "short", locale "de" → 400 VALIDATION; $.errors.email, $.errors.password, $.errors.locale present
@Test void loginIsCaseInsensitiveOnEmail()          // login "ANA@mail.com" → 200
@Test void loginWrongPasswordOrUnknownEmail()       // both → 401 $.code "BAD_CREDENTIALS"
@Test void meRequiresToken()                        // GET /api/me without token → 401 $.code "UNAUTHORIZED"; with token → 200 $.email
@Test void invalidBearerOnOpenGetIs401()            // GET /api/me with "Bearer garbage" → 401
@Test void refreshRotatesAndReuseRevokesAll()       // refresh with cookie A → 200 + cookie B (B ≠ A); refresh with A → 401; then refresh with B → 401
@Test void logoutRevokesRefreshToken()              // logout with cookie → 204 and Set-Cookie Max-Age=0; refresh with same cookie → 401
@Test void patchMeLocale()                          // PATCH {"locale":"me"} → 200 $.locale "me"; {"locale":"de"} → 400 VALIDATION
@Test void loginIsRateLimited()                     // 10 failed logins → 401 each; 11th → 429 $.code "RATE_LIMITED"
```

- [ ] **Step 2:** Run `./mvnw -q test -Dtest='AuthApiTest,RateLimiterTest'`. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - `application.yml` additions:
    ```yaml
    app:
      jwt.secret: ${JWT_SECRET:ZGV2LW9ubHktc2VjcmV0LWNoYW5nZS1tZS1pbi1wcm9kdWN0aW9uLTEyMzQ1Njc4OTA=}
      auth.cookie-secure: ${COOKIE_SECURE:true}
    ```
    The secret is base64; the decoded key must be ≥ 32 bytes.
  - `SecurityConfig`:
    - stateless sessions, CSRF disabled;
    - `permitAll` for `/api/auth/**`, `GET /api/**`, `/v3/api-docs/**` and `/swagger-ui/**`;
    - `authenticated` for any other `/api/**`;
    - `permitAll` for everything else (SPA files from M7);
    - `oauth2ResourceServer(jwt)` with a `NimbusJwtDecoder.withSecretKey(key).macAlgorithm(HS256)` bean;
    - an `authenticationEntryPoint` that writes ProblemDetail 401 `UNAUTHORIZED` as JSON;
    - beans `PasswordEncoder` (`BCryptPasswordEncoder`) and `Clock` (`Clock.systemUTC()`).
  - `TokenService.accessToken(UUID userId): String` uses `NimbusJwtEncoder` over `ImmutableSecret`, with claims `iss=sportsapp`, `sub=userId`, `iat`, `exp=+15m`, and header alg HS256.
  - `AuthService`:
    - `register` trims and lowercases the email, then checks uniqueness → `EMAIL_TAKEN`. It creates the user and calls `PlayerService.createOwn(userId, displayName.trim())`.
    - `login` returns `BAD_CREDENTIALS` for both an unknown email and a wrong password.
    - `issueRefresh(userId)` stores the hash with expiry +30 days and returns the raw token.
    - `refresh(raw)`:
      - unknown or expired → 401;
      - revoked → `revokeAllForUser` + 401;
      - otherwise revoke it and issue a new one.
  - `AuthController`:
    - calls `rateLimiter.check("login"|"register", request.getRemoteAddr())`;
    - sets the cookie with `ResponseCookie` (secure from `app.auth.cookie-secure`, `maxAge` 30 days);
    - a failed refresh also clears the cookie.

- [ ] **Step 4:** Run `./mvnw -q test`. Expected: all PASS.
- [ ] **Step 5:** Commit: `feat: register, login, refresh rotation, logout, /api/me`.

### Task 3: Frontend auth

**Files:**
- Create: `frontend/src/api/{client.ts,errors.ts,schema.d.ts}`, `frontend/src/auth/{AuthProvider.tsx,RequireAuth.tsx}`
- Create: `frontend/src/pages/auth/{LoginPage,RegisterPage}.tsx`
- Modify: `frontend/src/App.tsx`, `frontend/src/components/Layout.tsx`, both locale files
- Test: `frontend/src/api/client.test.ts`

**Interfaces:**
- Consumes: the Task 2 endpoints (regenerate `schema.d.ts` with `npm run gen:api`).
- Produces in `client.ts`:
  - `api` (openapi-fetch client using `fetch: authFetch` and `credentials: 'include'`)
  - `setAccessToken(token: string | null)`
  - `refreshAccessToken(): Promise<AuthResponse | null>` (single-flight)
  - `authFetch(req: Request): Promise<Response>`
  - `onSessionExpired(cb: () => void)`
- Produces in `errors.ts`:
  - `errorCode(err: unknown): string` (returns `UNKNOWN` when it can't tell)
  - `useNotifyError(): (err: unknown) => void` (Mantine notification with `t('errors.'+code)`)
  - `fieldErrors(err: unknown): Record<string, string>` (field → `t('validation.'+constraint)` key)
- Produces in `AuthProvider`:
  - `useAuth(): { user: Me | null; ready: boolean; login(email, pw); register(req); logout(); setLocale(l: 'me'|'en') }`
- Produces `RequireAuth`: redirects to `/login?next=<current path>` when `ready && !user`.
- Produces `safeNext(next: string | null): string`: returns `next` only if it starts with `/` and not `//`, otherwise `/`. Login and register navigate to `safeNext(searchParams.get('next'))`.

- [ ] **Step 1: Write the failing tests** (`client.test.ts`, with global `fetch` mocked by `vi.fn`):

```ts
test('adds bearer header when a token is set')
test('on 401 refreshes once, retries the original request with the new token, returns the retried response')
test('two concurrent 401s share one refresh call')
test('when refresh fails, returns the original 401, clears the token and calls onSessionExpired')
test('never tries to refresh for /api/auth/* requests')
test('safeNext keeps "/claim/abc", rejects "//evil.com", "https://evil.com" and null → "/"')
```

- [ ] **Step 2:** Run `npm test`. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - `authFetch` clones the request before sending, so the body can be replayed on retry.
  - `AuthProvider` calls `refreshAccessToken()` once on mount. On success it sets the user and calls `i18n.changeLanguage(user.locale)`.
  - The language switch in `Layout` calls `setLocale` when logged in, which does `PATCH /api/me` and changes the language.
  - Logged in, `Layout` shows the user's name and a Logout menu item. Logged out, it shows Login and Register links.
  - Routes: `/login`, `/register`, and `/` wrapped in `RequireAuth`.
  - Forms use Mantine `TextInput`/`PasswordInput` and show `fieldErrors`.
  - Add locale keys: `auth.*`, `errors.EMAIL_TAKEN`, `errors.BAD_CREDENTIALS`, `validation.NotBlank`, `validation.Email`, `validation.Size`, `validation.Pattern`.

- [ ] **Step 4:** Run `npm test && npm run build`. Expected: PASS.
- [ ] **Step 5: Verify manually.** With the backend and `npm run dev` running:
  1. Register; you land on home.
  2. Reload; you're still logged in.
  3. Switch to EN, log out, log back in; the UI is in EN.
  4. Log in with a wrong password; a translated error appears.
- [ ] **Step 6:** Commit: `feat: frontend auth with transparent token refresh`.
