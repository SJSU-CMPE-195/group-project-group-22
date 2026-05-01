package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.GameState;

/**
 * Snapshot of Pong state as seen by a single player.
 *
 * <p>Vertical layout: paddles sit at the top and bottom of the field and
 * slide horizontally.  The ball travels up/down.
 *
 * @param paddleX    left edge of this player's paddle (pixels)
 * @param ballX      ball centre X (pixels)
 * @param ballY      ball centre Y (pixels)
 * @param fieldWidth total field width in pixels, useful for AI boundary clamping
 * @param ballVelY   current Y velocity of the ball (negative = moving toward top,
 *                   positive = moving toward bottom)
 */
public record PongState(int paddleX, int ballX, int ballY, int fieldWidth, int ballVelY) implements GameState {}
