package com.sportsapp.competition;

import java.util.UUID;

/** Round is 1-based, position 0-based within its round; home/away are null until decided. */
public record PlannedMatch(int round, int position, UUID home, UUID away) {}
