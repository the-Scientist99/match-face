package com.sportsapp.competition;

import java.util.List;

public record BracketPlan(int rounds, List<PlannedMatch> matches) {}
