package edu.sjsu.spring2026.group32.pong.model;

import edu.sjsu.spring2026.group32.player.model.PlayerType;
import edu.sjsu.spring2026.group32.pong.ai.PongHardwareAI;

/**
 * Every selectable player option shown in each side's toolbar dropdown.
 *
 * <p>Rules enforced by the UI:
 * <ul>
 *   <li>{@link #HARDWARE} is grayed out when no {@link PongHardwareAI} was
 *       provided to the active Pong session (i.e., no device is connected).</li>
 *   <li>Software AI variants may be selected on both sides at the same time.</li>
 * </ul>
 *
 * <p>AI presets are parameterized in Pong's player factory:
 * <ul>
 *   <li>{@link #AI_EASY} — deadZone=40 px, reactionProb=0.55</li>
 *   <li>{@link #AI_HARD} — deadZone=8 px,  reactionProb=1.00</li>
 * </ul>
 */
public enum PlayerVariant {

    HUMAN    ("Human",    PlayerType.HUMAN),
    HARDWARE ("Hardware AI", PlayerType.HARDWARE),
    AI_EASY  ("Software AI Easy", PlayerType.SOFTWARE),
    AI_HARD  ("Software AI Hard", PlayerType.SOFTWARE);

    // ─────────────────────────────────────────────────────────────────────────

    private final String     displayName;
    private final PlayerType playerType;

    PlayerVariant(String displayName, PlayerType playerType) {
        this.displayName = displayName;
        this.playerType  = playerType;
    }

    /** Human-readable label shown in the toolbar dropdown. */
    public String getDisplayName() { return displayName; }

    /** The broad category this variant belongs to. */
    public PlayerType getPlayerType() { return playerType; }

    @Override
    public String toString() { return displayName; }
}
