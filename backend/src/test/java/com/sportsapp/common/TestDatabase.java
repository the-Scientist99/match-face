package com.sportsapp.common;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/** Wipes every table of a test database. Refuses any database whose name doesn't end in "_test". */
public final class TestDatabase {

  private TestDatabase() {}

  public static void truncateAll(DataSource dataSource) {
    // FOREIGN_KEY_CHECKS is per session, so every statement must run on this one connection.
    try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
      String db = c.getCatalog();
      if (db == null || !db.endsWith("_test")) {
        throw new IllegalStateException("Refusing to truncate tables in non-test database: " + db);
      }
      List<String> tables = new ArrayList<>();
      try (ResultSet rs = s.executeQuery("""
          SELECT table_name FROM information_schema.tables
          WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
          """)) {
        while (rs.next()) tables.add(rs.getString(1));
      }
      s.execute("SET FOREIGN_KEY_CHECKS = 0");
      try {
        for (String t : tables) s.execute("TRUNCATE TABLE `" + t + "`");
      } finally {
        s.execute("SET FOREIGN_KEY_CHECKS = 1");
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Could not truncate test database", e);
    }
  }
}
