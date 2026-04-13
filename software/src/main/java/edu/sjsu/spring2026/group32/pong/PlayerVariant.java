package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.PlayerType;

/**
 * Every selectable player option shown in each side's toolbar dropdown.
 *
 * <p>Rules enforced by the UI:
 * <ul>
 *   <li>The same variant <em>cannot</em> appear on both sides simultaneously.</li>
 *   <li>{@link #HUMAN} may only appear on one side (covered by the rule above,
 *       since HUMAN is a single variant).</li>
 *   <li>{@link #HARDWARE} is grayed out when no {@link PongHardwareAI} was
 *       provided to {@link PongGame} (i.e., no device is connected).</li>
 * </ul>
 *
 * <p>AI presets are parameterized inside {@link PongGame\#createPlayer}:
 * <ul>
 *   <li>{@link #AI_EASY} — deadZone=40 px, reactionProb=0.55</li>
 *   <li>{@link #AI_HARD} — deadZone=8 px,  reactionProb=1.00</li>
 * </ul>
 */
public enum PlayerVariant {

    HUMAN    ("Human",    PlayerType.HUMAN),
    HARDWARE ("Hardware", PlayerType.HARDWARE),
    AI_EASY  ("AI Easy",  PlayerType.SOFTWARE),
    AI_HARD  ("AI Hard",  PlayerType.SOFTWARE);

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
