package com.sportsapp.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Uses a DataSource that opens a new physical connection on every call, the worst case of a pool
 * handing out different connections, so the session-scoped FOREIGN_KEY_CHECKS setting must be
 * applied on the same connection as the TRUNCATEs.
 */
class TestDatabaseTest {

  private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
      System.getenv().getOrDefault("TEST_DB_URL", "jdbc:mysql://localhost:3306/sportsapp_test"), "sportsapp", "sportsapp");
  private final JdbcClient jdbc = JdbcClient.create(dataSource);

  @BeforeEach
  void createLinkedTables() {
    jdbc.sql("CREATE TABLE fk_parent (id INT PRIMARY KEY)").update();
    jdbc.sql("CREATE TABLE fk_child (id INT PRIMARY KEY, parent_id INT, FOREIGN KEY (parent_id) REFERENCES fk_parent(id))").update();
    jdbc.sql("INSERT INTO fk_parent VALUES (1)").update();
    jdbc.sql("INSERT INTO fk_child VALUES (1, 1)").update();
  }

  @AfterEach
  void dropLinkedTables() {
    jdbc.sql("DROP TABLE IF EXISTS fk_child").update();
    jdbc.sql("DROP TABLE IF EXISTS fk_parent").update();
  }

  @Test
  void truncatesTablesLinkedByForeignKeysEvenWhenEachCallGetsANewConnection() {
    TestDatabase.truncateAll(dataSource);

    assertThat(jdbc.sql("SELECT COUNT(*) FROM fk_parent").query(Integer.class).single()).isZero();
    assertThat(jdbc.sql("SELECT COUNT(*) FROM fk_child").query(Integer.class).single()).isZero();
  }
}
