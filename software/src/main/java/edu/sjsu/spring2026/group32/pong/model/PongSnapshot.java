package edu.sjsu.spring2026.group32.pong.model;

/**
 * Immutable view of the current Pong match state.
 */
public record PongSnapshot(
        int topPaddleX,
        int bottomPaddleX,
        int ballX,
        int ballY,
        int ballVelX,
        int ballVelY,
        int topScore,
        int bottomScore,
        int ballSpeedLevel,
        boolean constantSpeed,
        PongGameState gameState,
        long countdownStartMs) {
}
