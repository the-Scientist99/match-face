# M8: Photos and logos (implementation plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can add a profile photo to a player, a logo to a team, and a photo to a league or cup. Wherever the app shows the initials disc (`Crest`) today, it shows the image instead; without an image, the disc stays.

**Architecture:**
- The browser crops each upload to a square and resizes it before sending, using `createImageBitmap` and a canvas. That lets it decode HEIC and WebP, apply the EXIF rotation and drop EXIF data. The upload is then about 100 KB.
- The server never trusts what it receives:
  - It decodes the file with `ImageIO`, accepting only JPEG or PNG.
  - It rejects images larger than 8000×8000 px before decoding them.
  - It crops to a square, scales down to at most 256 px, and flattens the image onto white RGB.
  - It writes its own JPEG, so metadata and anything hidden in the original are gone.
- Files go on local disk under `app.images.dir`, named `<random uuid>.jpg`. Each upload gets a new name, so a URL never changes content and can be cached for a year.
- The `player`, `team` and `competition` rows each get a nullable `image` column holding that file name. API responses send `imageUrl` (`/api/images/<file>` or null).
- Images are served by Spring's static resource handler from the disk folder under `/api/images/**`. `GET /api/**` is already public, and the Vite dev proxy already forwards `/api`.

**Decided with the user (2026-10-29):** disk storage, not a bucket.

**Accepted trade-offs:**
- Image URLs are unguessable but not access-checked.
- A file whose transaction rolls back after it was written stays on disk as an orphan. Add a cleanup job if that ever matters.

**Tech Stack, Global Constraints, conventions:** [00-main-plan.md](00-main-plan.md). Follow the lock-first rule for writes and the explicit-file staging rule.

---

### Task 1: Schema and image store

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__images.sql`
- Create: `backend/src/main/java/com/sportsapp/image/ImageStore.java`
- Create: `backend/src/main/java/com/sportsapp/image/ImageConfig.java`
- Modify: `application.yml`, `src/test/resources/application-test.yml`, `.gitignore`
- Test: `backend/src/test/java/com/sportsapp/image/ImageStoreTest.java` (plain unit test, no Spring)

**V6:** `ALTER TABLE player ADD COLUMN image VARCHAR(40) NULL;` and the same for `team` and `competition`.

**Config:**
- `application.yml`:
  - `app.images.dir: ${IMAGES_DIR:./data/images}`
  - `spring.servlet.multipart.max-file-size: 5MB`
  - `spring.servlet.multipart.max-request-size: 5MB`
- Test profile: `app.images.dir: ${java.io.tmpdir}/sportsapp-test-images`
- Add `backend/data/` to `.gitignore`.

**`ImageStore`** (`@Component`, takes `@Value("${app.images.dir}") Path dir`, creates the folder at startup):
- `String save(InputStream in)`:
  1. Open an `ImageIO.createImageInputStream` and take the first reader. If there's none, or its `getFormatName()` isn't jpeg or png, throw `ApiException.badRequest("INVALID_IMAGE")`.
  2. Read `getWidth(0)` and `getHeight(0)` before decoding. If either is over 8000, throw the same error. This guards against decompression bombs.
  3. Decode, center-crop to a square, and scale to `min(side, 256)` with bicubic interpolation onto a `TYPE_INT_RGB` canvas filled white.
  4. Write a JPEG at quality 0.85 to `<uuid>.jpg.tmp`, `Files.move` it with `ATOMIC_MOVE` to `<uuid>.jpg`, and return `<uuid>.jpg`.
- `void deleteAfterCommit(@Nullable String file)`:
  - Registers a `TransactionSynchronization.afterCommit` that calls `Files.deleteIfExists`. Outside a transaction it deletes right away.
  - It ignores (logs) `IOException`, and ignores any name that isn't `<uuid>.jpg`, so a bad value can never point outside the folder.
- `static String url(@Nullable String file)`: returns `"/api/images/" + file`, or null.

**`ImageConfig`** (`WebMvcConfigurer`): `addResourceHandlers("/api/images/**")` → `"file:" + dir.toAbsolutePath() + "/"`, with `CacheControl.maxAge(365, DAYS).cachePublic().immutable()`.

- [ ] **Step 1: Write the failing tests.**
  - `ImageStoreTest` with `@TempDir`. Build test images in memory with `BufferedImage` and `ImageIO.write`.
  - `pngWithAlphaBecomes256SquareJpeg`: a 600×400 transparent PNG becomes a 256×256 JPEG that `ImageIO.read` can decode, and no `.tmp` file is left behind.
  - `smallImageIsNotUpscaled`: 100×80 in, 80×80 out.
  - `rejectsNonImagesAndOtherFormats`: random bytes, and a GIF, both give `INVALID_IMAGE`.
  - `rejectsHugeDimensions`: a PNG whose header claims 9000×10 px gives `INVALID_IMAGE`.
  - `deleteIgnoresNamesOutsideTheFolder`: `deleteAfterCommit("../x")` leaves a sibling file alone.
- [ ] **Step 2:** `./mvnw -q test -Dtest=ImageStoreTest` fails.
- [ ] **Step 3:** Implement. Run the tests again; they pass. Run the full suite, so V6 is checked by every integration test.

### Task 2: Upload and remove endpoints

**Files:**
- Create: `backend/src/main/java/com/sportsapp/image/ImageController.java`
- Create: `backend/src/main/java/com/sportsapp/image/ImageService.java`
- Modify: `player/ClaimService.java`
- Test: `backend/src/test/java/com/sportsapp/image/ImageApiTest.java`

**Endpoints** (multipart part name `file`; each returns `ImageResponse(String imageUrl)`):

| Method and path | Who may call it |
|---|---|
| `PUT` / `DELETE /api/players/{id}/image` | `PlayerService.canEdit`: the user's own player, or an unclaimed guest they created. Otherwise 403 (404 if the player doesn't exist). |
| `PUT` / `DELETE /api/teams/{id}/image` | `access.requireAdmin(TEAM)` |
| `PUT` / `DELETE /api/competitions/{id}/image` | `access.requireAdmin(COMPETITION)` |

**`ImageService.replace(type, userId, id, @Nullable MultipartFile file)`** (`@Transactional`; null `file` means remove):
1. Lock first: `SELECT image FROM <table> WHERE id = UUID_TO_BIN(:id) FOR UPDATE`. A missing row is a 404.
2. Check permission.
3. If there's a file, `store.save(file.getInputStream())`.
4. `UPDATE <table> SET image = :new`, then `store.deleteAfterCommit(old)`.
5. The table name comes from a fixed enum (`PLAYER→player`, `TEAM→team`, `COMPETITION→competition`) and is never built from input.

Plain JDBC on purpose: an image change shouldn't bump the competition's `@Version` and make other open tabs go STALE.

**Claims:** when a guest is merged into the claimer's player, the guest's image moves over if the claimer has none. Otherwise `deleteAfterCommit(guest image)`.

**Too-large uploads:** a request over 5 MB is rejected by Spring with 413. `ApiExceptionHandler` already turns the status name into the `code`. Assert the actual code in the test (`CONTENT_TOO_LARGE` or `PAYLOAD_TOO_LARGE`, depending on the Spring version), and use that key in the locales.

- [ ] **Step 1: Write the failing tests** (`MockMvc multipart(HttpMethod.PUT, …)`).
  - `uploadReplacesAndServesTheImage`:
    - A PUT returns an `imageUrl`, and a `GET` of that URL with no auth returns 200, `image/jpeg` and a `Cache-Control` containing `immutable`.
    - A second PUT returns a new URL; the old file is gone and the new one exists.
  - `removeClearsTheImage`: after a DELETE, `imageUrl` is null and the file is gone.
  - `permissions`:
    - Another user gets 403 on Ana's player, team and competition.
    - Ana can set an image on her unclaimed guest, but not once it's claimed.
    - A private team nobody can read gives 404.
  - `rejectsBadFiles`: a text file gives 400 `INVALID_IMAGE`, and a 6 MB file gives 413.
  - `claimKeepsAPhoto`: a claimed guest with a photo passes it to a claimer who has none.
- [ ] **Step 2:** Run them; they fail. **Step 3:** Implement. They pass, and so does the full suite.

### Task 3: `imageUrl` in responses

**Files:**
- Modify: `player/PlayerDtos.java`, `player/PlayerService.java`, `player/PlayerController.java`
- Modify: `team/TeamDtos.java`, `team/TeamService.java`
- Modify: `competition/CompetitionDtos.java`, `competition/CompetitionService.java`
- Modify: `match/MatchDtos.java`, `match/MatchViews.java`
- Test: extend the existing API tests with one assertion each

**Add `String imageUrl` (built with `ImageStore.url`) to:**
- `PlayerResponse`, `PlayerProfileResponse`
- `RosterPlayer`, `TeamResponse`, `TeamSummary`
- `CompetitionResponse`, `CompetitionSummary`
- `SideView` (the team's logo, null for pickup sides)
- `MatchSummary`, as `homeImageUrl` and `awayImageUrl`

`MatchViews.view` and `summaries` already join `team ht` / `team at`, so select `ht.image` and `at.image` there. JPA entities get a read-only `image` field (`@Column(insertable = false, updatable = false)`, so only `ImageService` writes it).

- [ ] **Step 1:** In `ImageApiTest`, after uploading a logo, check that it appears in: `/api/teams/mine`, `/api/teams/{id}`, a team match's `home.imageUrl`, and `/api/matches/mine`'s `homeImageUrl`. Likewise check the player photo in `/api/players/mine` and the profile, and the competition photo in `/api/competitions/mine`.
- [ ] **Step 2:** Implement, run the full suite, and fix any test that asserts whole JSON shapes.

### Task 4: Frontend

**Files:**
- Modify: `frontend/src/api/schema.d.ts` (regenerate with `npm run gen:api` against a running backend), `frontend/src/api/client.ts`
- Modify: `frontend/src/components/Crest.tsx`
- Create: `frontend/src/components/ImageUpload.tsx`
- Create: `frontend/src/components/squareImage.ts`
- Test: `frontend/src/components/squareImage.test.ts`
- Modify (pages listed below), `locales/en.json`, `locales/me.json`

**Types:** add every new `imageUrl` field to the `WithNull` lists in `client.ts`.

**`Crest`:** add an optional `src` prop. With `src`, it renders `<img className="crest" src={src} alt="" loading="lazy">`, sized the same and `object-fit: cover`. Without it, the initials disc as today. It stays decorative (`alt=""`), since the name is always written next to it.

**`squareImage.ts`:**
- `centerSquare(w, h) → { sx, sy, side }`: pure crop maths, unit-tested.
- `toSquareJpeg(file, size = 512): Promise<Blob>`:
  - Decode with `createImageBitmap(file)`, which applies the EXIF rotation by default.
  - Draw the `centerSquare` crop onto a `size`-px canvas, scaled down only (never up).
  - Export with `canvas.toBlob(…, 'image/jpeg', 0.9)`.
  - Reject with `INVALID_IMAGE` if the browser can't decode the file.

**`ImageUpload`** (`{ imageUrl, name, path, onChange }`):
- Shows a large `Crest`, then a Mantine `FileButton` (`accept="image/*"`) labelled "Change photo" or "Add photo", and a "Remove" button when there's an image.
- Uploads go through openapi-fetch with a `FormData` body (`bodySerializer`). Errors go through `useNotifyError`.
- After each change, it invalidates the affected queries: the entity, its lists, and `['matches']`.

**Where images appear** (each `Crest` gets `src`):

| Place | Image |
|---|---|
| `MatchList` `TeamLine` | `homeImageUrl` / `awayImageUrl` |
| `ScoreHeader` | `home.imageUrl` / `away.imageUrl` |
| `PlayersPage` | player photo |
| `PlayerProfilePage` header | player photo; `ImageUpload` when `canEdit` |
| `TeamPage` header | team logo; `ImageUpload` when `canAdmin` |
| `TeamPage` roster rows | player photos |
| `TeamsPage` | team logo |
| `HomePage` competition cards | competition photo (in place of the `ThemeIcon`, when there is one) |
| `CompetitionPage` header | competition photo; `ImageUpload` for admins |

**Locales** (both files):
- `image.add`, `image.change`, `image.remove`
- `errors.INVALID_IMAGE`: "That file isn't a photo we can use. Try a JPEG or PNG."
- The 413 code: "That photo is too big (max 5 MB)."

- [ ] **Step 1:** Write the `centerSquare` test: landscape, portrait, square. It fails.
- [ ] **Step 2:** Implement everything above. `npx tsc -b && npm test && npm run lint && npm run build` are all clean.

### Task 5: Deployment notes and check

- [ ] Update M7 Task 7.3 in `00-main-plan.md`:
  - Prod sets `IMAGES_DIR=/var/lib/sportsapp/images` (outside the jar's folder, owned by the app user).
  - Lightsail automatic snapshots back the folder up together with the database.
  - Any reverse proxy must allow request bodies of at least 5 MB.
- [ ] Add the M8 row to the milestone table in `00-main-plan.md`. M8 depends on M6 and can ship before M7.
- [ ] Browser check, run locally:
  - Upload a phone photo (portrait, with EXIF rotation) as a profile photo. It shows upright and square.
  - Add a team logo. It appears on the team page, in the teams list, on match cards and in the score header.
  - Add a competition photo. It appears on the home card and the competition header.
  - Remove each one. The initials come back.
  - A `.txt` renamed to `.jpg` is refused with the message.

**Skipped, add when wanted:**
- A crop and zoom editor (the center crop only).
- The user's photo in the header avatar (`MeResponse`).
- Crests in the standings table and the bracket.
- A cleanup job for orphaned files.
- Access-checked image URLs.
