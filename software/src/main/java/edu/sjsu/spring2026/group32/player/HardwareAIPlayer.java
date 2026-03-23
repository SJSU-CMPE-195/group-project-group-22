package edu.sjsu.spring2026.group32.player;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.HardwareSignalSource;

/**
 * Generic abstract base class for hardware-driven AI players.
 *
 * <p>Mirrors the design of {@link HumanPlayer}: it is completely game-agnostic
 * and can be used with any game that implements {@link GameState} /
 * {@link Action}.  Game-specific voltage interpretation is provided by
 * concrete subclasses that override {@link #voltageToAction(GameState, double)}.
 *
 * <p>Example usage (Hit The Zone):
 * <pre>{@code
 * public class HitTheZoneHardwareAI
 *         extends HardwareAIPlayer<HitTheZoneState, HitTheZoneAction> {
 *
 *     protected HitTheZoneAction voltageToAction(HitTheZoneState state,
 *                                                double voltage) {
 *         if (!state.inZone() || voltage < 1.0) return null;
 *         return HitTheZoneAction.SCORE;
 *     }
 * }
 * }</pre>
 */
public abstract class HardwareAIPlayer<S extends GameState, A extends Action>
        implements BasePlayer<S, A> {

    private final String           name;
    private final BaseSignalSource signalSource;

    /**
     * @param name         display name shown in the game HUD
     * @param signalSource voltage source (real serial hardware or a test stub)
     */
    protected HardwareAIPlayer(String name, BaseSignalSource signalSource) {
        this.name         = name;
        this.signalSource = signalSource;
    }

    /**
     * Translates the latest voltage reading and current game state into a
     * game-specific action.  Return {@code null} to indicate "no action this tick."
     *
     * <p>Called once per game tick from {@link #getNextMove(GameState)}.
     *
     * @param state   current game state
     * @param voltage latest voltage reading from the signal source (0.0–3.3 V)
     * @return the action to perform, or {@code null}
     */
    protected abstract A voltageToAction(S state, double voltage);

    /** Reads the latest voltage and delegates to {@link #voltageToAction}. */
    @Override
    public A getNextMove(S state) {
        return voltageToAction(state, signalSource.getNextVoltage());
    }

    @Override
    public String getName() { return name; }

    @Override
    public PlayerType getType() { return PlayerType.HARDWARE; }

    /**
     * Releases the underlying serial port.
     * Register this as a JVM shutdown hook so the port is always freed:
     * <pre>{@code
     * Runtime.getRuntime().addShutdownHook(new Thread(hwPlayer::close));
     * }</pre>
     */
    public void close() {
        if (signalSource instanceof HardwareSignalSource h) {
            h.close();
        }
    }
}
