package com.sportsapp.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsapp.common.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UuidStorageTest extends IntegrationTest {

  @Autowired UserAccountRepository users;

  @Test
  void idsAreUuidV7StoredAsStandardBinary() {
    UserAccount u = users.save(new UserAccount("a@test.dev", "x", "en"));
    assertThat(u.getId().version()).isEqualTo(7);
    String viaSql = jdbc.sql("SELECT BIN_TO_UUID(id) FROM user_account WHERE email = 'a@test.dev'").query(String.class).single();
    assertThat(viaSql).isEqualTo(u.getId().toString());
    assertThat(jdbc.sql("SELECT COUNT(*) FROM user_account WHERE id = UUID_TO_BIN(:id)")
        .param("id", u.getId().toString()).query(Integer.class).single()).isEqualTo(1);
    assertThat(users.findById(u.getId())).isPresent();
  }
}
