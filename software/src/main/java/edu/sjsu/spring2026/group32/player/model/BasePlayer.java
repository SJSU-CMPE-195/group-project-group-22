package edu.sjsu.spring2026.group32.player.model;

public interface BasePlayer<S extends GameState, A extends Action> {
    A getNextMove(S state);

    String getName();
    PlayerType getType();
}
