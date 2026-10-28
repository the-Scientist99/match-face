package com.sportsapp.competition;

import java.util.UUID;

/** {@code seed} null: placed by random draw after the seeded teams. */
public record SeededTeam(UUID teamId, Integer seed) {}
