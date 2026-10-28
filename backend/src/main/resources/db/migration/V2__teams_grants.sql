CREATE TABLE admin_grant (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  user_id BINARY(16) NOT NULL, resource_type VARCHAR(20) NOT NULL, resource_id BINARY(16) NOT NULL, role VARCHAR(20) NOT NULL,
  UNIQUE (user_id, resource_type, resource_id), INDEX (resource_type, resource_id),
  FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);
CREATE TABLE team (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL, name VARCHAR(50) NOT NULL, sport VARCHAR(20) NOT NULL
);
CREATE TABLE team_player (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  team_id BINARY(16) NOT NULL, player_id BINARY(16) NOT NULL,
  UNIQUE (team_id, player_id), INDEX (player_id),
  FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES player(id) ON DELETE CASCADE
);
-- Emails are stored lowercased, so compare them byte for byte: the default collation also ignores
-- accents and would treat ana@ and ána@ as one address.
ALTER TABLE user_account MODIFY email VARCHAR(254) NOT NULL COLLATE utf8mb4_bin;
