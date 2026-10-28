CREATE TABLE competition (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL, sport VARCHAR(20) NOT NULL, type VARCHAR(10) NOT NULL,
  visibility VARCHAR(10) NOT NULL, status VARCHAR(10) NOT NULL, double_round_robin BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE competition_team (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  competition_id BINARY(16) NOT NULL, team_id BINARY(16) NOT NULL, seed INT NULL,
  UNIQUE (competition_id, team_id), UNIQUE (competition_id, seed), INDEX (team_id),
  FOREIGN KEY (competition_id) REFERENCES competition(id) ON DELETE CASCADE,
  FOREIGN KEY (team_id) REFERENCES team(id)
);
CREATE TABLE stage (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  competition_id BINARY(16) NOT NULL, type VARCHAR(20) NOT NULL, name VARCHAR(50) NOT NULL, sequence_no INT NOT NULL,
  INDEX (competition_id),
  FOREIGN KEY (competition_id) REFERENCES competition(id) ON DELETE CASCADE
);
ALTER TABLE sport_match ADD CONSTRAINT fk_match_stage FOREIGN KEY (stage_id) REFERENCES stage(id) ON DELETE CASCADE;
