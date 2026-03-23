package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.player.HardwareAIPlayer;

/**
 * Hardware AI player for the Hit The Zone proof-of-concept game.
 *
 * <p>Reads the live voltage produced by the neural-signal hardware via a
 * {@link BaseSignalSource} and returns {@link HitTheZoneAction#SCORE} when
 * two conditions are both true:
 * <ol>
 *   <li>The ball is currently inside the zone ({@code state.inZone() == true}).</li>
 *   <li>The measured voltage meets or exceeds {@code voltageThreshold}.</li>
 * </ol>
 *
 * <p><b>Edge-detection:</b> one SCORE per zone pass is enforced by
 * {@link PoC_HitTheZone}'s game loop via its {@code lastActions} array — the
 * same mechanism used for every other player type — so no internal state is
 * needed here.
 *
 * <p><b>Threshold choice:</b> the default 1.0 V mirrors the LED threshold in
 * {@code Read_neuron_with_serial.ino} ({@code if (voltage > 1.0)}) so
 * firmware and software agree on what constitutes a "signal".
 */
public class HitTheZoneHardwareAI
        extends HardwareAIPlayer<HitTheZoneState, HitTheZoneAction> {

    /** Mirrors the LED threshold in Read_neuron_with_serial.ino. */
    private static final double DEFAULT_THRESHOLD = 1.0;

    private final double voltageThreshold;

    /**
     * @param name             display name shown in the game HUD
     * @param signalSource     voltage source (real serial hardware or a test stub)
     * @param voltageThreshold minimum voltage required to register a SCORE (volts)
     */
    public HitTheZoneHardwareAI(String name, BaseSignalSource signalSource,
                                double voltageThreshold) {
        super(name, signalSource);
        this.voltageThreshold = voltageThreshold;
    }

    /**
     * Convenience constructor using the default 1.0 V threshold.
     *
     * @param name         display name shown in the game HUD
     * @param signalSource voltage source (real serial hardware or a test stub)
     */
    public HitTheZoneHardwareAI(String name, BaseSignalSource signalSource) {
        this(name, signalSource, DEFAULT_THRESHOLD);
    }

    /**
     * Returns {@link HitTheZoneAction#SCORE} when the ball is in the zone and
     * voltage is at or above the threshold; {@code null} otherwise.
     */
    @Override
    protected HitTheZoneAction voltageToAction(HitTheZoneState state,
                                               double voltage) {
        if (!state.inZone())            return null; // ball not in the zone
        if (voltage < voltageThreshold) return null; // signal too weak
        return HitTheZoneAction.SCORE;
    }
}
