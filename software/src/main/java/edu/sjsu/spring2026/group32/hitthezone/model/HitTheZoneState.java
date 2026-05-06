package edu.sjsu.spring2026.group32.hitthezone.model;

import edu.sjsu.spring2026.group32.player.model.GameState;

public record HitTheZoneState(boolean inZone) implements GameState {}
