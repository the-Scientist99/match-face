package com.sportsapp.competition;

import java.util.UUID;

/** A finished league match's score. */
public record Result(UUID home, UUID away, int homeGoals, int awayGoals) {}
