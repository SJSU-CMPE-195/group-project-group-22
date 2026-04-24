package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.NeuralHardwareConfig;
import edu.sjsu.spring2026.group32.hardware.VoltageInjector;
import edu.sjsu.spring2026.group32.player.HardwareAIPlayer;

/**
 * Hardware AI player for the Hit The Zone proof-of-concept game.
 *
 * <h3>Voltage injection (ball in zone)</h3>
 * <p>On every zone <em>entry</em> this player sends a constant
 * {@link #INJECT_VOLTAGE} stimulus to the neural hardware via its
 * {@link VoltageInjector}. On every zone <em>exit</em> it sends a stop
 * command so the hardware reverts to normal ADC sampling. This keeps
 * all serial-protocol knowledge inside the hardware player and away from
 * the game loop.
 *
 * <h3>Spike reading (one spike = one action)</h3>
 * <p>Returns {@link HitTheZoneAction#SCORE} on the <em>rising edge</em> of a
 * spike: the first tick where voltage crosses from below to at-or-above
 * {@code voltageThreshold} while the ball is in the zone. Subsequent ticks
 * where voltage remains high return {@code null} because the spike has already
 * been registered. When voltage falls back below the threshold the internal
 * {@code wasFiring} flag resets, so the next genuine spike is detected as a
 * new rising edge.
 *
 * <h3>Edge detection</h3>
 * <p>Rising-edge detection is performed internally via the {@code wasFiring}
 * flag, ensuring exactly one {@code SCORE} action per spike regardless of how
 * long the voltage stays above threshold. The flag is also reset on every
 * zone entry and exit for a clean start on each zone pass.
 *
 * <h3>Threshold choice</h3>
 * <p>The default threshold is shared through
 * {@link NeuralHardwareConfig#DEFAULT_FIRING_THRESHOLD_VOLTS} so
 * hardware-backed games use the same firing baseline unless they
 * explicitly override it.
 */
public class HitTheZoneHardwareAI
        extends HardwareAIPlayer<HitTheZoneState, HitTheZoneAction> {

    /** Constant voltage injected into the hardware while the ball is in the zone. */
    static final double INJECT_VOLTAGE = 2.0;

    private final VoltageInjector injector;
    private final double voltageThreshold;

    /** Tracks the zone state from the previous tick to detect entry/exit edges. */
    private boolean wasInZone = false;

    /**
     * Rising-edge flag: true while voltage is at-or-above threshold within the
     * current spike. Resets to false when voltage drops below threshold or on
     * zone entry/exit so that each new spike produces exactly one SCORE.
     */
    private boolean wasFiring = false;

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
     * {@link HitTheZoneAction#SCORE} on the rising edge of a spike, or
     * {@code null} otherwise.
     */
    @Override
    protected HitTheZoneAction voltageToAction(HitTheZoneState state, double voltage) {
        if (!wasInZone && state.inZone()) {
            injector.injectVoltage(INJECT_VOLTAGE);
            wasFiring = false;
        } else if (wasInZone && !state.inZone()) {
            injector.stopInjection();
            wasFiring = false;
        }
        wasInZone = state.inZone();

        if (!state.inZone()) {
            return null;
        }
        if (voltage >= voltageThreshold) {
            if (!wasFiring) {
                wasFiring = true;
                return HitTheZoneAction.SCORE;
            }
            return null;
        }

        wasFiring = false;
        return null;
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
        injector.stopInjection();
        wasInZone = false;
        wasFiring = false;
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
