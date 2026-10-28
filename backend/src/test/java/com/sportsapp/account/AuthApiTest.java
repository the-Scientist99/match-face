package com.sportsapp.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class AuthApiTest extends IntegrationTest {

  private ResultActions registerRaw(String email, String password, String displayName, String locale) throws Exception {
    return mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(toJson(Map.of(
        "email", email, "password", password, "displayName", displayName, "locale", locale))));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
        .content(toJson(Map.of("email", email, "password", password))));
  }

  private ResultActions refresh(Cookie cookie) throws Exception {
    return mvc.perform(post("/api/auth/refresh").cookie(cookie));
  }

  private static Cookie refreshCookie(MvcResult r) {
    Cookie c = r.getResponse().getCookie("refresh_token");
    assertThat(c).isNotNull();
    return new Cookie(c.getName(), c.getValue());
  }

  @Test
  void registerNormalizesEmailCreatesPlayerAndSetsCookie() throws Exception {
    MvcResult r = registerRaw(" Ana@Mail.com ", "password123", "Ana", "en")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.email").value("ana@mail.com"))
        .andExpect(jsonPath("$.user.displayName").value("Ana"))
        .andExpect(jsonPath("$.user.playerId").value(notNullValue()))
        .andExpect(jsonPath("$.accessToken").value(not(emptyString())))
        .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
        .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
        .andExpect(header().string("Set-Cookie", containsString("Secure")))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
        .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
        .andReturn();
    JsonNode user = json.readTree(r.getResponse().getContentAsString()).get("user");
    var row = jdbc.sql("SELECT BIN_TO_UUID(id) id, BIN_TO_UUID(user_id) u, BIN_TO_UUID(created_by_user_id) c FROM player")
        .query().singleRow();
    assertThat(row.get("id")).isEqualTo(user.get("playerId").asString());
    assertThat(row.get("u")).isEqualTo(user.get("id").asString());
    assertThat(row.get("c")).isEqualTo(user.get("id").asString());
  }

  @Test
  void registerDuplicateEmailIgnoringCase() throws Exception {
    registerRaw("ana@mail.com", "password123", "Ana", "en").andExpect(status().isCreated());
    registerRaw("ana@MAIL.com", "password123", "Ana", "en")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void registerValidation() throws Exception {
    registerRaw("nope", "short", "Ana", "de")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION"))
        .andExpect(jsonPath("$.errors.email").exists())
        .andExpect(jsonPath("$.errors.password").exists())
        .andExpect(jsonPath("$.errors.locale").exists());
  }

  @Test
  void registerRejectsPasswordOver72Bytes() throws Exception {
    registerRaw("ana@mail.com", "č".repeat(40), "Ana", "en") // 40 chars, 80 bytes: BCrypt can't hash it
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
  }

  @Test
  void loginIsCaseInsensitiveOnEmail() throws Exception {
    registerRaw("ana@mail.com", "password123", "Ana", "en").andExpect(status().isCreated());
    login("ANA@mail.com", "password123")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("ana@mail.com"))
        .andExpect(header().string("Set-Cookie", containsString("refresh_token=")));
  }

  @Test
  void loginWrongPasswordOrUnknownEmail() throws Exception {
    registerRaw("ana@mail.com", "password123", "Ana", "en").andExpect(status().isCreated());
    login("ana@mail.com", "wrong-password").andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    login("nobody@mail.com", "password123").andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
  }

  @Test
  void meRequiresToken() throws Exception {
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    TestUser ana = register("Ana");
    mvc.perform(get("/api/me").with(as(ana))).andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ana@test.dev"))
        .andExpect(jsonPath("$.playerId").value(ana.playerId().toString()));
  }

  @Test
  void invalidBearerOnOpenGetIs401() throws Exception {
    mvc.perform(get("/api/me").header("Authorization", "Bearer garbage"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void refreshRotatesAndReuseRevokesAll() throws Exception {
    Cookie a = refreshCookie(registerRaw("ana@mail.com", "password123", "Ana", "en").andReturn());
    Cookie b = refreshCookie(refresh(a).andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value(not(emptyString())))
        .andExpect(jsonPath("$.user.email").value("ana@mail.com"))
        .andReturn());
    assertThat(b.getValue()).isNotEqualTo(a.getValue());
    rewindRotations(31);
    refresh(a).andExpect(status().isUnauthorized())
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    refresh(b).andExpect(status().isUnauthorized());
  }

  /** Moves every revocation into the past, beyond the reuse grace window when seconds > 30. */
  private void rewindRotations(int seconds) {
    jdbc.sql("UPDATE refresh_token SET revoked_at = revoked_at - INTERVAL :s SECOND WHERE revoked_at IS NOT NULL")
        .param("s", seconds).update();
  }

  @Test
  void reuseRightAfterRotationIsAGraceRefreshNotTheft() throws Exception {
    // a reload or a second tab that sent the old cookie a moment after another refresh rotated it
    Cookie a = refreshCookie(registerRaw("ana@mail.com", "password123", "Ana", "en").andReturn());
    Cookie b = refreshCookie(refresh(a).andExpect(status().isOk()).andReturn());
    Cookie c = refreshCookie(refresh(a).andExpect(status().isOk()).andReturn());
    refresh(b).andExpect(status().isOk());
    refresh(c).andExpect(status().isOk());
  }

  @Test
  void reusingALoggedOutTokenIsNeverGraced() throws Exception {
    Cookie a = refreshCookie(registerRaw("ana@mail.com", "password123", "Ana", "en").andReturn());
    mvc.perform(post("/api/auth/logout").cookie(a)).andExpect(status().isNoContent());
    refresh(a).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutWithExpiredBearerStillRevokes() throws Exception {
    // the SPA sends its (possibly expired) access token on every call, auth endpoints included
    Cookie a = refreshCookie(registerRaw("ana@mail.com", "password123", "Ana", "en").andReturn());
    mvc.perform(post("/api/auth/logout").cookie(a).header("Authorization", "Bearer garbage"))
        .andExpect(status().isNoContent());
    refresh(a).andExpect(status().isUnauthorized());
  }

  @Test
  void refreshWithoutCookieIs401() throws Exception {
    mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void logoutRevokesRefreshToken() throws Exception {
    Cookie a = refreshCookie(registerRaw("ana@mail.com", "password123", "Ana", "en").andReturn());
    mvc.perform(post("/api/auth/logout").cookie(a))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    refresh(a).andExpect(status().isUnauthorized());
  }

  @Test
  void patchMeLocale() throws Exception {
    TestUser ana = register("Ana");
    mvc.perform(patch("/api/me").with(as(ana)).contentType(APPLICATION_JSON).content("{\"locale\":\"me\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locale").value("me"))
        .andExpect(jsonPath("$.displayName").value("Ana"));
    mvc.perform(patch("/api/me").with(as(ana)).contentType(APPLICATION_JSON).content("{\"locale\":\"de\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION"));
  }

  @Test
  void patchMeDisplayNameRenamesOwnPlayer() throws Exception {
    TestUser ana = register("Ana");
    mvc.perform(patch("/api/me").with(as(ana)).contentType(APPLICATION_JSON).content("{\"displayName\":\" Bob \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Bob"))
        .andExpect(jsonPath("$.locale").value("en"));
    assertThat(jdbc.sql("SELECT name FROM player").query(String.class).single()).isEqualTo("Bob");
    mvc.perform(patch("/api/me").with(as(ana)).contentType(APPLICATION_JSON).content("{\"displayName\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION"))
        .andExpect(jsonPath("$.errors.displayName").exists());
  }

  @Test
  void patchMeRequiresToken() throws Exception {
    mvc.perform(patch("/api/me").contentType(APPLICATION_JSON).content("{\"locale\":\"me\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void loginIsRateLimited() throws Exception {
    for (int i = 0; i < 10; i++) login("ana@mail.com", "wrong-password").andExpect(status().isUnauthorized());
    login("ana@mail.com", "wrong-password")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }
}
