-- teams asking to join a public competition; a row is a request waiting for the organizer
ALTER TABLE competition ADD COLUMN entries_open BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE competition_entry (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  competition_id BINARY(16) NOT NULL, team_id BINARY(16) NOT NULL,
  UNIQUE (competition_id, team_id), INDEX (team_id),
  FOREIGN KEY (competition_id) REFERENCES competition(id) ON DELETE CASCADE,
  FOREIGN KEY (team_id) REFERENCES team(id)
);
