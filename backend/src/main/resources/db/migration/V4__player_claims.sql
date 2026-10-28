CREATE TABLE player_claim (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  player_id BINARY(16) NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE, expires_at DATETIME(6) NOT NULL,
  FOREIGN KEY (player_id) REFERENCES player(id) ON DELETE CASCADE
);
