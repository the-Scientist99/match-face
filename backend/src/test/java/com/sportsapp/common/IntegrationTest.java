package com.sportsapp.common;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcClient jdbc;
  @Autowired protected JsonMapper json;

  @Autowired private DataSource dataSource;
  @Autowired private RateLimiter rateLimiter;

  protected record TestUser(UUID id, UUID playerId, String token) {}

  @BeforeEach
  void truncateAllTables() {
    TestDatabase.truncateAll(dataSource);
    rateLimiter.reset();
  }

  /** Registers {@code <name lowercased>@test.dev} with password {@code password123} and locale {@code en}. */
  protected TestUser register(String name) throws Exception {
    String body = mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(toJson(Map.of(
            "email", name.toLowerCase() + "@test.dev", "password", "password123", "displayName", name, "locale", "en"))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    JsonNode n = json.readTree(body);
    return new TestUser(UUID.fromString(n.get("user").get("id").asString()),
        UUID.fromString(n.get("user").get("playerId").asString()), n.get("accessToken").asString());
  }

  protected UUID createGuest(TestUser owner, String name) throws Exception {
    String body = mvc.perform(post("/api/players").with(as(owner)).contentType(APPLICATION_JSON).content(toJson(Map.of("name", name))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return UUID.fromString(json.readTree(body).get("id").asString());
  }

  protected RequestPostProcessor as(TestUser u) {
    return req -> {
      req.addHeader("Authorization", "Bearer " + u.token());
      return req;
    };
  }

  protected String toJson(Object body) {
    return json.writeValueAsString(body);
  }
}
