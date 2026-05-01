package edu.sjsu.spring2026.group32.hitthezone.ai;

import edu.sjsu.spring2026.group32.hardware.signal.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.NeuralHardwareConfig;
import edu.sjsu.spring2026.group32.hardware.signal.VoltageInjector;
import edu.sjsu.spring2026.group32.player.HardwareAIPlayer;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;

/**
 * Hardware AI player for the Hit The Zone proof-of-concept game.
 *
 * <h3>Voltage injection (ball in zone)</h3>
 * <p>On every zone <em>entry</em> this player sends a constant
 * {@link NeuralHardwareConfig#CONSTANT_INJECT_VOLTAGE} stimulus to the neural hardware via its
 * {@link VoltageInjector}. On every zone <em>exit</em> it sends a stop
 * command so the hardware reverts to normal ADC sampling. This keeps
 * all serial-protocol knowledge inside the hardware player and away from
 * the game loop.
 *
 * <h3>Spike reading</h3>
 * <p>Returns {@link HitTheZoneAction#SCORE} on the rising edge of a spike:
 * the first in-zone tick where voltage crosses from below to at-or-above
 * {@code voltageThreshold}. Subsequent high-voltage ticks from the same spike
 * return {@code null} until the signal falls back below the threshold.
 *
 * <h3>Threshold choice</h3>
 * <p>The default threshold is shared through
 * {@link NeuralHardwareConfig#DEFAULT_FIRING_THRESHOLD_VOLTS} so
 * hardware-backed games use the same firing baseline unless they
 * explicitly override it.
 */
public class HitTheZoneHardwareAI
        extends HardwareAIPlayer<HitTheZoneState, HitTheZoneAction> {
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final double LOGGED_VOLTAGE_MIN = 0.85;

    private final VoltageInjector injector;
    private final double voltageThreshold;

    /** Tracks the zone state from the previous tick to detect entry/exit edges. */
    private boolean wasInZone = false;

    /**
     * Canonical constructor.
     *
     * @param name display name shown in the game HUD
     * @param signalSource voltage source (real serial hardware or a test stub)
     * @param injector used to send inject/stop commands to the ESP32;
     *                 pass {@link VoltageInjector#NONE} for software-only runs
     * @param voltageThreshold minimum voltage required to register a SCORE (volts)
     */
    public HitTheZoneHardwareAI(String name,
                                BaseSignalSource signalSource,
                                VoltageInjector injector,
                                double voltageThreshold) {
        super(name, signalSource);
        this.injector = injector;
        this.voltageThreshold = voltageThreshold;
    }

    private void debug(String message) {
        System.out.printf("[HTZ-AI %s] %s%n", getName(), message);
    }

    private void debugGreen(String message) {
        System.out.printf(ANSI_GREEN + "[HTZ-AI %s] %s" + ANSI_RESET + "%n", getName(), message);
    }

    private void updateInjectionForZoneTransition(HitTheZoneState state) {
        if (!wasInZone && state.inZone()) {
            debug(String.format("zone enter -> inject %.3fV", NeuralHardwareConfig.CONSTANT_INJECT_VOLTAGE));
            injector.injectVoltage(NeuralHardwareConfig.CONSTANT_INJECT_VOLTAGE);
        } else if (wasInZone && !state.inZone()) {
            debug("zone exit -> stop injection");
            injector.stopInjection();
            // Voltage returns to 0 after injection stops, which naturally resets
            // the parser's lastWasAbove flag before the next zone entry.
        }
        wasInZone = state.inZone();
    }

    private HitTheZoneAction actionFromVoltage(HitTheZoneState state, double voltage) {
        if (!state.inZone()) {
            // Late-fire contract: a genuine new spike (rising edge) that occurs after
            // zone exit still registers a SCORE event.  Using sourceHasSpike() here —
            // rather than a stateless voltage comparison — prevents the plateau of a
            // spike already counted in-zone from spilling over into a spurious out-of-zone
            // attempt when the ball exits while voltage is still high.  Without this,
            // every in-zone hit produces one extra uncredited processScore() call,
            // inflating the attempts counter and halving the displayed accuracy.
            if (sourceHasSpike(voltageThreshold)) {
                debug(String.format("score %.3fV (late-fire, out of zone)", voltage));
                return HitTheZoneAction.SCORE;
            }
            return null;
        }
        if (voltage >= LOGGED_VOLTAGE_MIN) {
            debug(String.format("in-zone voltage %.3fV (threshold %.3fV)", voltage, voltageThreshold));
        }
        // Rising-edge detection is delegated to NeuralSignalParser.hasSpike() via
        // HardwareAIPlayer.sourceHasSpike().  A multi-tick spike plateau produces
        // exactly one SCORE instead of one per game tick.
        if (sourceHasSpike(voltageThreshold)) {
            debugGreen(String.format("emit SCORE at %.3fV", voltage));
            return HitTheZoneAction.SCORE;
        }
        return null;
    }

    /**
     * Convenience constructor using the shared default threshold.
     *
     * @param name display name shown in the game HUD
     * @param signalSource voltage source (real serial hardware or a test stub)
     * @param injector used to send inject/stop commands to the ESP32
     */
    public HitTheZoneHardwareAI(String name,
                                BaseSignalSource signalSource,
                                VoltageInjector injector) {
        this(name, signalSource, injector, NeuralHardwareConfig.DEFAULT_FIRING_THRESHOLD_VOLTS);
    }

    /**
     * Convenience constructor with no injection and a custom threshold.
     *
     * @param name display name shown in the game HUD
     * @param signalSource voltage source (real serial hardware or a test stub)
     * @param voltageThreshold minimum voltage required to register a SCORE (volts)
     */
    public HitTheZoneHardwareAI(String name,
                                BaseSignalSource signalSource,
                                double voltageThreshold) {
        this(name, signalSource, VoltageInjector.NONE, voltageThreshold);
    }

    /**
     * Convenience constructor using the shared default threshold with no injection.
     *
     * @param name display name shown in the game HUD
     * @param signalSource voltage source (real serial hardware or a test stub)
     */
    public HitTheZoneHardwareAI(String name, BaseSignalSource signalSource) {
        this(name, signalSource, VoltageInjector.NONE, NeuralHardwareConfig.DEFAULT_FIRING_THRESHOLD_VOLTS);
    }

    /**
     * Fires inject/stop commands on zone transitions, then returns
     * {@link HitTheZoneAction#SCORE} only when the hardware voltage crosses the
     * threshold from below while the ball is in the zone.
     */
    @Override
    protected HitTheZoneAction voltageToAction(HitTheZoneState state, double voltage) {
        updateInjectionForZoneTransition(state);
        return actionFromVoltage(state, voltage);
    }

    /**
     * Stops any active injection without taking ownership of the serial port.
     *
     * <p>This is used by Launcher-managed windows that share a live
     * {@link edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager}:
     * the game must stop stimulating the hardware when its window closes, but
     * it must not disconnect the shared port out from under the Launcher.</p>
     */
    public void stopInjectionOnly() {
        debug("stopInjectionOnly()");
        injector.stopInjection();
        wasInZone = false;
        // The voltage drops to 0 once injection stops, which naturally resets
        // the parser's lastWasAbove flag before the next session.
    }

    /**
     * Stops any active injection, then releases the underlying serial port.
     * Register as a JVM shutdown hook so ports are always freed:
     * <pre>{@code
     * Runtime.getRuntime().addShutdownHook(new Thread(hwPlayer::close));
     * }</pre>
     */
    @Override
    public void close() {
        stopInjectionOnly();
        super.close();
    }
}
