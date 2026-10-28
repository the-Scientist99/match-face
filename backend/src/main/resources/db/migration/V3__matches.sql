CREATE TABLE sport_match (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
  sport VARCHAR(20) NOT NULL, stage_id BINARY(16) NULL, round INT NULL, bracket_position INT NULL,
  home_team_id BINARY(16) NULL, away_team_id BINARY(16) NULL,
  home_label VARCHAR(30) NULL, away_label VARCHAR(30) NULL,
  kickoff_at DATETIME(6) NULL, venue VARCHAR(100) NULL, status VARCHAR(20) NOT NULL,
  visibility VARCHAR(10) NULL, penalty_winner_side VARCHAR(4) NULL,
  next_match_id BINARY(16) NULL, next_slot VARCHAR(4) NULL,
  INDEX (stage_id), INDEX (kickoff_at),
  FOREIGN KEY (home_team_id) REFERENCES team(id), FOREIGN KEY (away_team_id) REFERENCES team(id),
  FOREIGN KEY (next_match_id) REFERENCES sport_match(id) ON DELETE SET NULL
);
CREATE TABLE match_lineup (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  match_id BINARY(16) NOT NULL, player_id BINARY(16) NOT NULL, side VARCHAR(4) NOT NULL, in_goal BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (match_id, player_id), INDEX (player_id),
  FOREIGN KEY (match_id) REFERENCES sport_match(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES player(id)
);
CREATE TABLE match_event (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  match_id BINARY(16) NOT NULL, type VARCHAR(10) NOT NULL, side VARCHAR(4) NOT NULL,
  player_id BINARY(16) NULL, assist_player_id BINARY(16) NULL,
  minute SMALLINT NULL CHECK (minute BETWEEN 0 AND 150),
  INDEX (match_id), INDEX (player_id), INDEX (assist_player_id),
  FOREIGN KEY (match_id) REFERENCES sport_match(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES player(id), FOREIGN KEY (assist_player_id) REFERENCES player(id)
);
