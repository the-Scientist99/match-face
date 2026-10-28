package com.sportsapp.player;

import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsapp.common.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class PlayerApiTest extends IntegrationTest {

  private ResultActions rename(TestUser u, UUID playerId, String name) throws Exception {
    return mvc.perform(patch("/api/players/{id}", playerId).with(as(u)).contentType(APPLICATION_JSON)
        .content("{\"name\":\"" + name + "\"}"));
  }

  @Test
  void createGuestTrimsName() throws Exception {
    TestUser ana = register("Ana");
    mvc.perform(post("/api/players").with(as(ana)).contentType(APPLICATION_JSON).content("{\"name\":\"  Marko \"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Marko"))
        .andExpect(jsonPath("$.claimed").value(false))
        .andExpect(jsonPath("$.canEdit").value(true));
  }

  @Test
  void createGuestRequiresAuth() throws Exception {
    mvc.perform(post("/api/players").contentType(APPLICATION_JSON).content("{\"name\":\"Marko\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void blankName() throws Exception {
    TestUser ana = register("Ana");
    mvc.perform(post("/api/players").with(as(ana)).contentType(APPLICATION_JSON).content("{\"name\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION"))
        .andExpect(jsonPath("$.errors.name").value("NotBlank"));
  }

  @Test
  void creatorRenamesGuest() throws Exception {
    TestUser ana = register("Ana");
    UUID marko = createGuest(ana, "Marko");
    rename(ana, marko, "Marko P").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Marko P"));
    mvc.perform(get("/api/players/{id}", marko)).andExpect(jsonPath("$.name").value("Marko P"))
        .andExpect(jsonPath("$.canEdit").value(false));
  }

  @Test
  void othersCannotRenameGuest() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    UUID marko = createGuest(ana, "Marko");
    rename(ivan, marko, "Hacked").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(get("/api/players/{id}", marko).with(as(ivan))).andExpect(jsonPath("$.name").value("Marko"))
        .andExpect(jsonPath("$.canEdit").value(false));
  }

  @Test
  void userRenamesOwnPlayerButCreatorCannot() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    rename(ana, ana.playerId(), "Ana B").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ana B"));
    // a claim (M4) merges a guest into the user's own player, so another user's player is never renamable by its guest's creator
    rename(ana, ivan.playerId(), "Nope").andExpect(status().isForbidden());
    rename(ivan, ivan.playerId(), "Ivan C").andExpect(status().isOk()).andExpect(jsonPath("$.claimed").value(true));
  }

  @Test
  void mineListsOwnFirstThenCreatedByName() throws Exception {
    TestUser ana = register("Ana");
    TestUser ivan = register("Ivan");
    createGuest(ana, "Zed");
    createGuest(ana, "bo");
    createGuest(ivan, "Aaron");
    mvc.perform(get("/api/players/mine").with(as(ana)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name").value(contains("Ana", "bo", "Zed")))
        .andExpect(jsonPath("$[0].id").value(ana.playerId().toString()));
  }

  @Test
  void mineRequiresAuth() throws Exception {
    mvc.perform(get("/api/players/mine")).andExpect(status().isUnauthorized());
  }

  @Test
  void unknownPlayer() throws Exception {
    mvc.perform(get("/api/players/{id}", "01a18400-0000-7000-8000-000000000000"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
