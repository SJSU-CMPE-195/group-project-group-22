package edu.sjsu.spring2026.group32.hitthezone.ai;

import edu.sjsu.spring2026.group32.player.model.BasePlayer;
import edu.sjsu.spring2026.group32.player.model.PlayerType;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;

import java.util.Random;

/**
 * A software AI player for Hit The Zone that plays "perfectly" — it always
 * intends to score on every zone pass — but is humanized by a configurable
 * jitter expressed as a maximum reaction delay in game ticks.
 *
 * <p>On each new zone entry the AI waits between 0 and {@code maxJitterTicks}
 * ticks before returning {@link HitTheZoneAction#SCORE}.  If the ball exits
 * the zone before the countdown expires, the opportunity is missed.  This is
 * the <em>only</em> source of inaccuracy: the AI never deliberately misses,
 * it just sometimes hesitates too long.
 *
 * <ul>
 *   <li>{@code maxJitterTicks = 0} — reacts on the very first tick in zone
 *       (effectively perfect, ~100 % accuracy).</li>
 *   <li>{@code maxJitterTicks = 5} — reacts 0–5 ticks after entering the zone
 *       (~83 ms window at 60 fps), occasionally missing fast passes.</li>
 * </ul>
 */
public class HitTheZoneSoftwareAI implements BasePlayer<HitTheZoneState, HitTheZoneAction> {

    private final String name;
    private final int    maxJitterTicks;
    private final Random random = new Random();

    /**
     * Countdown to the moment this AI presses SCORE.
     * {@code -1} means the AI is not currently tracking a zone entry.
     */
    private int reactionCountdown = -1;

    /**
     * @param name           display name shown in the HUD
     * @param maxJitterTicks maximum reaction delay in ticks (0 = perfectly instant)
     */
    public HitTheZoneSoftwareAI(String name, int maxJitterTicks) {
        this.name           = name;
        this.maxJitterTicks = Math.max(0, maxJitterTicks);
    }

    /**
     * Returns {@link HitTheZoneAction#SCORE} exactly once per zone entry, after
     * a random delay in {@code [0, maxJitterTicks]} ticks.  Returns {@code null}
     * (no action) on every other tick.
     */
    @Override
    public HitTheZoneAction getNextMove(HitTheZoneState state) {
        if (state.inZone()) {
            // First tick inside the zone for this entry — arm the countdown.
            if (reactionCountdown == -1) {
                reactionCountdown = (maxJitterTicks == 0) ? 0
                        : random.nextInt(maxJitterTicks + 1);
            }

            if (reactionCountdown == 0) {
                reactionCountdown = -1;           // consumed; reset for next entry
                return HitTheZoneAction.SCORE;
            }

            reactionCountdown--;                  // still waiting

        } else {
            // Ball left the zone — disarm so the next entry triggers afresh.
            reactionCountdown = -1;
        }

        return null;                              // no action this tick
    }

    @Override
    public String getName() { return name; }

    @Override
    public PlayerType getType() { return PlayerType.SOFTWARE; }
}
