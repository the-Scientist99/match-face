package com.sportsapp.match;

public enum Side {
  HOME, AWAY;

  public Side opposite() {
    return this == HOME ? AWAY : HOME;
  }
}
