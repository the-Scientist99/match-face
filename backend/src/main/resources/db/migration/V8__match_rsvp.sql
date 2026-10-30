-- "I'm in / I'm out" from players on a team match's rosters; side is the roster they answered from
CREATE TABLE match_rsvp (
  id BINARY(16) PRIMARY KEY, created_at DATETIME(6) NOT NULL,
  match_id BINARY(16) NOT NULL, player_id BINARY(16) NOT NULL, side VARCHAR(4) NOT NULL, going BOOLEAN NOT NULL,
  UNIQUE (match_id, player_id), INDEX (player_id),
  FOREIGN KEY (match_id) REFERENCES sport_match(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES player(id) ON DELETE CASCADE
);
