package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.player.GameState;

public record HitTheZoneState(boolean inZone) implements GameState {}
