package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.BasePlayer;
import edu.sjsu.spring2026.group32.player.PlayerType;

public class PongSoftwareAI implements BasePlayer<PongState, PongAction> {
    private final String name;

    public PongSoftwareAI(String name) {
        this.name = name;
    }

    @Override
    public PongAction getNextMove(PongState state) {
        final int PADDLE_HEIGHT = 100; // Paddle height is 100 px

        int paddleCenter = state.paddleY() + PADDLE_HEIGHT / 2;

        // Add dead-zone of 10 pixels to stop jittering
        if (state.ballY() < paddleCenter - 10) {
            return PongAction.UP;
        } else if (state.ballY() > paddleCenter + 10) {
            return PongAction.DOWN;
        }
        return PongAction.IDLE;
    }

    @Override
    public String getName() { return this.name; }

    @Override
    public PlayerType getType() { return PlayerType.SOFTWARE; }
}
