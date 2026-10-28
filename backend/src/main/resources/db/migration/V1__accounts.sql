CREATE TABLE user_account (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL, locale VARCHAR(2) NOT NULL
);
CREATE TABLE refresh_token (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  user_id BINARY(16) NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME(6) NOT NULL, revoked_at DATETIME(6) NULL, rotated BOOLEAN NOT NULL DEFAULT FALSE,
  FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);
CREATE TABLE player (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  name VARCHAR(50) NOT NULL, user_id BINARY(16) NULL UNIQUE, created_by_user_id BINARY(16) NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user_account(id),
  FOREIGN KEY (created_by_user_id) REFERENCES user_account(id)
);
