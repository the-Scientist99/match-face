package com.sportsapp.sport;

import com.sportsapp.match.EventType;
import com.sportsapp.match.Side;

/** The part of a match event the score depends on. */
public record ScoreEvent(EventType type, Side side) {}
