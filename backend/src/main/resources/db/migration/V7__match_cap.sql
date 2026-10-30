-- the most players one side of a pickup can have; NULL for no limit
ALTER TABLE sport_match ADD COLUMN max_per_side INT NULL;
