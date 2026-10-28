package com.sportsapp.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseSmokeTest extends IntegrationTest {

  @Test
  void usesTestDatabase() {
    assertThat(jdbc.sql("SELECT DATABASE()").query(String.class).single()).isEqualTo("sportsapp_test");
  }
}
