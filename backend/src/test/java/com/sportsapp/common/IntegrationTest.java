package com.sportsapp.common;

import java.util.List;
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

  @BeforeEach
  void truncateAllTables() {
    String db = jdbc.sql("SELECT DATABASE()").query(String.class).single();
    if (db == null || !db.endsWith("_test")) {
      throw new IllegalStateException("Refusing to truncate tables in non-test database: " + db);
    }
    List<String> tables = jdbc.sql("""
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
        """).query(String.class).list();
    jdbc.sql("SET FOREIGN_KEY_CHECKS = 0").update();
    tables.forEach(t -> jdbc.sql("TRUNCATE TABLE `" + t + "`").update());
    jdbc.sql("SET FOREIGN_KEY_CHECKS = 1").update();
  }
}
