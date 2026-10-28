package com.sportsapp.common;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcClient jdbc;
  @Autowired protected JsonMapper json;

  @Autowired private DataSource dataSource;

  @BeforeEach
  void truncateAllTables() {
    TestDatabase.truncateAll(dataSource);
  }
}
