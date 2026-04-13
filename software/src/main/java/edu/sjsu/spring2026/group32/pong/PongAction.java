package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.Action;

/**
 * Actions available to a Pong paddle in the vertical layout.
 *
 * <p>Paddles sit at the top and bottom of the field and slide
 * <em>horizontally</em>, so movement is LEFT / RIGHT rather than UP / DOWN.
 */
public enum PongAction implements Action {
    LEFT, RIGHT, IDLE
}
