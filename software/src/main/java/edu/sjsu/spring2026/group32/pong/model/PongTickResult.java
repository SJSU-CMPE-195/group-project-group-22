package edu.sjsu.spring2026.group32.pong.model;

/**
 * Outcome produced by a single engine tick.
 */
public enum PongTickResult {
    NONE,
    TOP_SCORED,
    BOTTOM_SCORED,
    RALLY_RESET
}
