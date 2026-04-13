package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.BasePlayer;
import edu.sjsu.spring2026.group32.player.PlayerType;

import java.util.Random;

/**
 * Parameterised software AI for vertical Pong.
 *
 * <p>The AI tracks the ball X position relative to its own paddle centre.
 * Two parameters control perceived difficulty:
 * <ul>
 *   <li>reactionDeadZone: half-width dead zone in pixels around the paddle
 *       centre. Larger values mean sloppier tracking. Typical range: 5-60 px.</li>
 *   <li>reactionProbability: 0.0-1.0 chance of acting each tick.
 *       1.0 = perfect reaction; 0.55 = misses ~45% of ticks.</li>
 * </ul>
 *
 * <p>Two ready-made presets are wired in PongGame#createPlayer:
 * <ul>
 *   <li>AI Easy: deadZone=22, reactionProb=0.72</li>
 *   <li>AI Hard: deadZone=8,  reactionProb=1.00</li>
 * </ul>
 */
public class PongSoftwareAI implements BasePlayer<PongState, PongAction> {

    /** Must match PongGame.PADDLE_WIDTH. */
    private static final int PADDLE_WIDTH = 80;

    private final String name;
    private final int    reactionDeadZone;
    private final double reactionProbability;
    private final Random rng;

    /**
     * @param name                display label shown in score overlay and popup
     * @param reactionDeadZone    half-width dead zone in pixels (>= 0)
     * @param reactionProbability chance of acting each tick (0.0-1.0)
     */
    public PongSoftwareAI(String name, int reactionDeadZone, double reactionProbability) {
        this.name                = name;
        this.reactionDeadZone    = Math.max(0, reactionDeadZone);
        this.reactionProbability = Math.max(0.0, Math.min(1.0, reactionProbability));
        this.rng                 = new Random();
    }

    @Override
    public PongAction getNextMove(PongState state) {
        // Probabilistic skip: simulate slower reflexes
        if (rng.nextDouble() > reactionProbability) return PongAction.IDLE;

        int paddleCenter = state.paddleX() + PADDLE_WIDTH / 2;
        int diff         = state.ballX() - paddleCenter;

        if (diff < -reactionDeadZone) return PongAction.LEFT;
        if (diff >  reactionDeadZone) return PongAction.RIGHT;
        return PongAction.IDLE;
    }

    @Override public String     getName() { return name;                }
    @Override public PlayerType getType() { return PlayerType.SOFTWARE; }
}
