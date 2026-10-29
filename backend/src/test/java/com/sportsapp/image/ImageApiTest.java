package com.sportsapp.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class ImageApiTest extends IntegrationTest {

  @Autowired ImageStore store;

  private static byte[] png() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  private ResultActions upload(TestUser u, String path, byte[] bytes) throws Exception {
    return mvc.perform(multipart(HttpMethod.PUT, path).file(new MockMultipartFile("file", "photo.png", "image/png", bytes)).with(as(u)));
  }

  /** Uploads and returns the new image URL. */
  private String uploaded(TestUser u, String path) throws Exception {
    String body = upload(u, path, png()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("imageUrl").asString();
  }

  private Path fileOf(String url) {
    return store.dir().resolve(url.substring("/api/images/".length()));
  }

  private UUID createCompetition(TestUser owner) throws Exception {
    String body = mvc.perform(post("/api/competitions").with(as(owner)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("name", "League", "type", "LEAGUE", "visibility", "PUBLIC", "doubleRoundRobin", false))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asString());
  }

  @Test
  void uploadReplacesAndServesTheImage() throws Exception {
    TestUser ana = register("Ana");
    UUID team = createTeam(ana, "Alpha");
    String first = uploaded(ana, "/api/teams/" + team + "/image");
    assertThat(first).matches("/api/images/[0-9a-f-]{36}\\.jpg");
    mvc.perform(get(first))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/jpeg"))
        .andExpect(header().string("Cache-Control", containsString("immutable")));
    String second = uploaded(ana, "/api/teams/" + team + "/image");
    assertThat(second).isNotEqualTo(first);
    assertThat(fileOf(first)).doesNotExist();
    assertThat(fileOf(second)).exists();
  }

  @Test
  void removeClearsTheImage() throws Exception {
    TestUser ana = register("Ana");
    String url = uploaded(ana, "/api/players/" + ana.playerId() + "/image");
    mvc.perform(delete("/api/players/{id}/image", ana.playerId()).with(as(ana)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").doesNotExist());
    assertThat(fileOf(url)).doesNotExist();
    assertThat(jdbc.sql("SELECT image FROM player WHERE id = UUID_TO_BIN(:id)").param("id", ana.playerId().toString())
        .query(String.class).optional()).isEmpty();
  }

  @Test
  void permissions() throws Exception {
    TestUser ana = register("Ana");
    TestUser zoe = register("Zoe");
    UUID team = createTeam(ana, "Alpha");
    UUID competition = createCompetition(ana);
    UUID guest = createGuest(ana, "Guest");
    byte[] png = png();
    upload(zoe, "/api/teams/" + team + "/image", png).andExpect(status().isNotFound()); // can't even read it
    putOnRoster(team, zoe.playerId());
    upload(zoe, "/api/teams/" + team + "/image", png).andExpect(status().isForbidden());
    upload(zoe, "/api/competitions/" + competition + "/image", png).andExpect(status().isForbidden());
    upload(zoe, "/api/players/" + ana.playerId() + "/image", png).andExpect(status().isForbidden());
    upload(zoe, "/api/players/" + guest + "/image", png).andExpect(status().isForbidden());
    upload(ana, "/api/players/" + guest + "/image", png).andExpect(status().isOk());
    upload(ana, "/api/competitions/" + competition + "/image", png).andExpect(status().isOk());
    upload(ana, "/api/players/" + UUID.randomUUID() + "/image", png).andExpect(status().isNotFound());
    mvc.perform(multipart(HttpMethod.PUT, "/api/teams/" + team + "/image").file(new MockMultipartFile("file", png)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsFilesThatArentPhotos() throws Exception {
    TestUser ana = register("Ana");
    upload(ana, "/api/players/" + ana.playerId() + "/image", "not a photo".getBytes())
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
  }

  @Test
  void imagesShowInResponses() throws Exception {
    TestUser ana = register("Ana");
    UUID alpha = createTeam(ana, "Alpha", ana.playerId());
    UUID beta = createTeam(ana, "Beta");
    UUID competition = createCompetition(ana);
    String logo = uploaded(ana, "/api/teams/" + alpha + "/image");
    String photo = uploaded(ana, "/api/players/" + ana.playerId() + "/image");
    String cover = uploaded(ana, "/api/competitions/" + competition + "/image");
    String body = mvc.perform(post("/api/matches").with(as(ana)).contentType(APPLICATION_JSON)
            .content(toJson(Map.of("homeTeamId", alpha, "awayTeamId", beta, "visibility", "PRIVATE",
                "kickoffAt", java.time.Instant.now().plusSeconds(3600).toString()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.home.imageUrl").value(logo))
        .andExpect(jsonPath("$.away.imageUrl").doesNotExist())
        .andReturn().getResponse().getContentAsString();
    mvc.perform(get("/api/matches/mine").with(as(ana)))
        .andExpect(jsonPath("$[0].id").value(json.readTree(body).get("id").asString()))
        .andExpect(jsonPath("$[0].homeImageUrl").value(logo))
        .andExpect(jsonPath("$[0].awayImageUrl").doesNotExist());
    mvc.perform(get("/api/teams/mine").with(as(ana))).andExpect(jsonPath("$[0].imageUrl").value(logo));
    mvc.perform(get("/api/teams/{id}", alpha).with(as(ana)))
        .andExpect(jsonPath("$.imageUrl").value(logo))
        .andExpect(jsonPath("$.players[0].imageUrl").value(photo));
    mvc.perform(get("/api/players/mine").with(as(ana))).andExpect(jsonPath("$[0].imageUrl").value(photo));
    mvc.perform(get("/api/players/{id}", ana.playerId())).andExpect(jsonPath("$.imageUrl").value(photo));
    mvc.perform(get("/api/competitions/mine").with(as(ana))).andExpect(jsonPath("$[0].imageUrl").value(cover));
    mvc.perform(get("/api/competitions/{id}", competition).with(as(ana))).andExpect(jsonPath("$.imageUrl").value(cover));
  }

  @Test
  void claimKeepsTheGuestsPhotoIfTheClaimerHasNone() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID guest = createGuest(ana, "Ivan G");
    String url = uploaded(ana, "/api/players/" + guest + "/image");
    String body = mvc.perform(post("/api/players/{id}/claims", guest).with(as(ana)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    mvc.perform(post("/api/players/claims/{token}", json.readTree(body).get("token").asString()).with(as(ivan)))
        .andExpect(status().isOk());
    assertThat(jdbc.sql("SELECT image FROM player WHERE id = UUID_TO_BIN(:id)").param("id", ivan.playerId().toString())
        .query(String.class).single()).isEqualTo(url.substring("/api/images/".length()));
    assertThat(Files.exists(fileOf(url))).isTrue();
  }
}
