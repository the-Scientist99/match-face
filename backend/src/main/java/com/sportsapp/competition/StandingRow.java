package com.sportsapp.competition;

import java.util.UUID;

public record StandingRow(int position, UUID teamId, String name, int played, int won, int drawn, int lost,
    int goalsFor, int goalsAgainst, int goalDifference, int points) {}
