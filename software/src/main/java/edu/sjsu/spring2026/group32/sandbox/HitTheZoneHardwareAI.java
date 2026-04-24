package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.VoltageInjector;
import edu.sjsu.spring2026.group32.player.HardwareAIPlayer;

/**
 * Hardware AI player for the Hit The Zone proof-of-concept game.
 *
 * <h3>Voltage injection (ball in zone)</h3>
 * <p>On every zone <em>entry</em> this player sends a constant
 * {@link #INJECT_VOLTAGE} stimulus to the neural hardware via its
 * {@link VoltageInjector}.  On every zone <em>exit</em> it sends a stop
 * command so the hardware reverts to normal ADC sampling.  This keeps
 * all serial-protocol knowledge inside the hardware player and away from
 * the game loop.
 *
 * <h3>Spike reading (one spike = one action)</h3>
 * <p>Returns {@link HitTheZoneAction#SCORE} on the <em>rising edge</em> of a
 * spike — the first tick where voltage crosses from below to at-or-above
 * {@code voltageThreshold} while the ball is in the zone.  Subsequent ticks
 * where voltage remains high return {@code null} (the spike has already been
 * registered).  When voltage falls back below the threshold the internal
 * {@code wasFiring} flag resets, so the next genuine spike is detected as a
 * new rising edge.
 *
 * <h3>Edge-detection</h3>
 * <p>Rising-edge detection is performed internally via the {@code wasFiring}
 * flag, ensuring exactly one {@code SCORE} action per spike regardless of how
 * long the voltage stays above threshold.  The flag is also reset on every
 * zone entry and exit for a clean start on each zone pass.
 *
 * <h3>Threshold choice</h3>
 * <p>The default 1.0 V mirrors the LED threshold in
 * {@code Read_neuron_with_serial.ino} ({@code if (voltage > 1.0)}) so
 * firmware and software agree on what constitutes a "signal".
 */
public class HitTheZoneHardwareAI
        extends HardwareAIPlayer<HitTheZoneState, HitTheZoneAction> {

    /** Constant voltage injected into the hardware while the ball is in the zone. */
    static final double INJECT_VOLTAGE = 2.0;

    /** Mirrors the LED threshold in Read_neuron_with_serial.ino. */
    private static final double DEFAULT_THRESHOLD = 1.0;

    private final VoltageInjector injector;
    private final double          voltageThreshold;

    /** Tracks the zone state from the previous tick to detect entry/exit edges. */
    private boolean wasInZone = false;

    /**
     * Rising-edge flag: true while voltage is at-or-above threshold within the
     * current spike.  Resets to false when voltage drops below threshold (or on
     * zone entry/exit) so that each new spike produces exactly one SCORE.
     */
    private boolean wasFiring = false;

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Canonical constructor.
     *
     * @param name             display name shown in the game HUD
     * @param signalSource     voltage source (real serial hardware or a test stub)
     * @param injector         used to send inject/stop commands to the ESP32;
     *                         pass {@link VoltageInjector#NONE} for software-only runs
     * @param voltageThreshold minimum voltage required to register a SCORE (volts)
     */
    public HitTheZoneHardwareAI(String name,
                                BaseSignalSource signalSource,
                                VoltageInjector injector,
                                double voltageThreshold) {
        super(name, signalSource);
        this.injector         = injector;
        this.voltageThreshold = voltageThreshold;
    }

    /**
     * Convenience constructor — real injector, default 1.0 V threshold.
     *
     * @param name         display name shown in the game HUD
     * @param signalSource voltage source (real serial hardware or a test stub)
     * @param injector     used to send inject/stop commands to the ESP32
     */
    public HitTheZoneHardwareAI(String name,
                                BaseSignalSource signalSource,
                                VoltageInjector injector) {
        this(name, signalSource, injector, DEFAULT_THRESHOLD);
    }

    /**
     * Convenience constructor — no injection (software-only / test), custom threshold.
     *
     * @param name             display name shown in the game HUD
     * @param signalSource     voltage source (real serial hardware or a test stub)
     * @param voltageThreshold minimum voltage required to register a SCORE (volts)
     */
    public HitTheZoneHardwareAI(String name,
                                BaseSignalSource signalSource,
                                double voltageThreshold) {
        this(name, signalSource, VoltageInjector.NONE, voltageThreshold);
    }

    /**
     * Convenience constructor — no injection (software-only / test), default 1.0 V threshold.
     *
     * @param name         display name shown in the game HUD
     * @param signalSource voltage source (real serial hardware or a test stub)
     */
    public HitTheZoneHardwareAI(String name, BaseSignalSource signalSource) {
        this(name, signalSource, VoltageInjector.NONE, DEFAULT_THRESHOLD);
    }

    // =========================================================================
    // Game logic
    // =========================================================================

    /**
     * Fires inject/stop commands on zone transitions, then returns
     * {@link HitTheZoneAction#SCORE} on the rising edge of a spike (the first
     * tick voltage meets or exceeds the threshold after being below it), or
     * {@code null} otherwise.
     */
    @Override
    protected HitTheZoneAction voltageToAction(HitTheZoneState state, double voltage) {
        // ---- Zone transition: send injection commands to hardware ----
        if (!wasInZone && state.inZone()) {
            injector.injectVoltage(INJECT_VOLTAGE);
            wasFiring = false; // fresh start for each zone pass
        } else if (wasInZone && !state.inZone()) {
            injector.stopInjection();
            wasFiring = false; // reset so next zone entry begins clean
        }
        wasInZone = state.inZone();

        // ---- Spike detection: one SCORE per rising edge ----
        if (!state.inZone()) {
            return null; // ball not in the zone
        }
        if (voltage >= voltageThreshold) {
            if (!wasFiring) {
                // Rising edge — neuron just fired; register exactly one action.
                wasFiring = true;
                return HitTheZoneAction.SCORE;
            }
            // Voltage still high from the same spike — already registered.
            return null;
        } else {
            // Voltage below threshold — spike (if any) has ended; ready for the next one.
            wasFiring = false;
            return null;
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Stops any active injection, then releases the underlying serial port.
     * Register as a JVM shutdown hook so ports are always freed:
     * <pre>{@code
     * Runtime.getRuntime().addShutdownHook(new Thread(hwPlayer::close));
     * }</pre>
     */
    @Override
    public void close() {
        injector.stopInjection(); // ensure hardware isn't left injecting
        super.close();            // disconnect the serial port
    }
}
