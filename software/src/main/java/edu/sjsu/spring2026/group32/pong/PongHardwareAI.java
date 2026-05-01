package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.HardwareSignalSource;
import edu.sjsu.spring2026.group32.hardware.NeuralHardwareConfig;
import edu.sjsu.spring2026.group32.hardware.VoltageInjector;
import edu.sjsu.spring2026.group32.player.BasePlayer;
import edu.sjsu.spring2026.group32.player.PlayerType;

/**
 * Hardware AI player for vertical Pong.
 *
 * <h3>Dual-channel 6-neuron firmware (NeuralSerial_DualChannel_6Neuron)</h3>
 * <ul>
 *   <li>Channel 1 (GPIO34 / DAC1) — left 3-neuron group: controls LEFT movement</li>
 *   <li>Channel 2 (GPIO35 / DAC2) — right 3-neuron group: controls RIGHT movement</li>
 * </ul>
 *
 * <h3>Injection strategy</h3>
 * <p>On every game tick {@link #getNextMove} first checks whether the ball is
 * moving <em>toward</em> the hardware player (negative {@code ballVelY}).
 * Injection is suppressed entirely while the ball travels away; any active
 * channel is stopped at that transition.  When the ball is incoming, the
 * offset between the ball's X position and the paddle center determines which
 * channel to drive: more than {@link #DEAD_ZONE} pixels to the left → CH1
 * (left neurons); to the right → CH2 (right neurons).  When crossing sides
 * the previous channel is explicitly stopped before the new one is started
 * (per the firmware's {@code STOP_INJECT_CHn} / {@code INJECT_V_CHn}
 * protocol).  Within the dead zone both channels are stopped so the paddle
 * coasts.
 *
 * <h3>Action mapping</h3>
 * <p>After updating injection, the ADC readings are sampled:
 * CH1 voltage ≥ threshold → {@link PongAction#LEFT},
 * CH2 voltage ≥ threshold → {@link PongAction#RIGHT},
 * otherwise → {@link PongAction#IDLE}.
 * If both channels fire simultaneously LEFT takes precedence.
 *
 * <h3>Wiring example (in Launcher / PongGame)</h3>
 * <pre>{@code
 *   NeuralSignalParser  p0         = new NeuralSignalParser(0);   // CH1 = GPIO34 = LEFT
 *   NeuralSignalParser  p1         = new NeuralSignalParser(1);   // CH2 = GPIO35 = RIGHT
 *   HardwareSignalSource leftSrc   = new HardwareSignalSource(pongManager, p0);
 *   HardwareSignalSource rightSrc  = new HardwareSignalSource(pongManager, p1);
 *   PongHardwareAI hw = new PongHardwareAI("Hardware", leftSrc, rightSrc, leftSrc);
 * }</pre>
 * {@code leftSrc} is passed as the {@link VoltageInjector} because
 * {@link HardwareSignalSource} implements both interfaces and both sources
 * share the same serial connection, so either can send injection commands.
 */
public class PongHardwareAI implements BasePlayer<PongState, PongAction> {

    /**
     * Half-width dead zone around the paddle center (pixels).
     * Derived from paddle width so the hardware player stops injecting when
     * the ball is already close enough that no correction is needed.
     * {@code PADDLE_WIDTH / 4} = 20 px at the default 80 px paddle width.
     */
    private static final int DEAD_ZONE = PongGame.PADDLE_WIDTH / 4;

    /** Tracks which channel is currently being injected to avoid redundant serial commands. */
    private enum InjectState { NONE, LEFT, RIGHT }

    private final String           name;
    private final BaseSignalSource leftSource;
    private final BaseSignalSource rightSource;
    private final VoltageInjector  injector;
    private final double           threshold;

    private InjectState lastInject       = InjectState.NONE;

    /**
     * Guards all injection calls.  {@code false} by default — injection only
     * starts once the HARDWARE variant is explicitly selected in the game UI.
     * Set via {@link #setInjectionEnabled(boolean)}.
     */
    private volatile boolean injectionEnabled = false;

    /**
     * Number of spike events detected since the last reset.
     * Incremented each game tick a channel's voltage meets or exceeds the
     * firing threshold.  Reset via {@link #resetSpikeCount()}.
     */
    private volatile int spikeCount = 0;

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Full constructor.
     *
     * @param name        display name shown in scoreboard and overlays
     * @param leftSource  ADC signal source for LEFT movement (CH1 / GPIO34)
     * @param rightSource ADC signal source for RIGHT movement (CH2 / GPIO35)
     * @param injector    used to send {@code INJECT_V_CHn} / {@code STOP_INJECT_CHn}
     *                    commands to the ESP32; pass {@link VoltageInjector#NONE}
     *                    for software-only runs or unit tests
     * @param threshold   voltage at or above which a direction fires (0.0–3.3 V)
     */
    public PongHardwareAI(String name,
                          BaseSignalSource leftSource,
                          BaseSignalSource rightSource,
                          VoltageInjector  injector,
                          double           threshold) {
        this.name        = name;
        this.leftSource  = leftSource;
        this.rightSource = rightSource;
        this.injector    = injector;
        this.threshold   = threshold;
    }

    /**
     * Constructor without injector and a custom threshold.
     * Injection is a no-op ({@link VoltageInjector#NONE}); the player only reads.
     *
     * @param name        display name shown in scoreboard and overlays
     * @param leftSource  ADC signal source for LEFT movement (CH1 / GPIO34)
     * @param rightSource ADC signal source for RIGHT movement (CH2 / GPIO35)
     * @param threshold   voltage at or above which a direction fires (0.0–3.3 V)
     */
    public PongHardwareAI(String name,
                          BaseSignalSource leftSource,
                          BaseSignalSource rightSource,
                          double           threshold) {
        this(name, leftSource, rightSource, VoltageInjector.NONE, threshold);
    }

    /**
     * Convenience constructor — no injector, shared default firing threshold.
     *
     * @param name        display name shown in scoreboard and overlays
     * @param leftSource  ADC signal source for LEFT movement (CH1 / GPIO34)
     * @param rightSource ADC signal source for RIGHT movement (CH2 / GPIO35)
     */
    public PongHardwareAI(String name,
                          BaseSignalSource leftSource,
                          BaseSignalSource rightSource) {
        this(name, leftSource, rightSource, VoltageInjector.NONE,
             NeuralHardwareConfig.DEFAULT_FIRING_THRESHOLD_VOLTS);
    }

    // =========================================================================
    // BasePlayer
    // =========================================================================

    /**
     * Injects voltage on the appropriate channel based on ball-vs-paddle offset,
     * then reads both ADC channels for a discrete spike using rising-edge detection.
     *
     * <p>{@link BaseSignalSource#hasSpike(double)} is used instead of a raw
     * {@code >= threshold} comparison so that a single biological spike spanning
     * multiple game ticks is counted and acted upon exactly once.
     *
     * <p>Injection state transitions:
     * <ul>
     *   <li>Ball moving away (ballVelY &ge; 0) → stop all injection, skip stimulation</li>
     *   <li>Ball incoming, &gt; DEAD_ZONE left of center → stop CH2 (if active), inject CH1</li>
     *   <li>Ball incoming, &gt; DEAD_ZONE right of center → stop CH1 (if active), inject CH2</li>
     *   <li>Ball incoming, within DEAD_ZONE              → stop all injection</li>
     * </ul>
     */
    @Override
    public PongAction getNextMove(PongState state) {
        updateInjection(state);
        if (leftSource.hasSpike(threshold))  { spikeCount++; return PongAction.LEFT;  }
        if (rightSource.hasSpike(threshold)) { spikeCount++; return PongAction.RIGHT; }
        return PongAction.IDLE;
    }

    /** Returns the number of spike events detected since the last {@link #resetSpikeCount()}. */
    public int getSpikeCount() { return spikeCount; }

    /** Resets the spike counter to zero. Call after each ball hit or miss. */
    public void resetSpikeCount() { spikeCount = 0; }

    @Override public String     getName() { return name;                }
    @Override public PlayerType getType() { return PlayerType.HARDWARE; }

    // =========================================================================
    // Injection management
    // =========================================================================

    /**
     * Enables or disables voltage injection.
     *
     * <p>Call with {@code true} when the HARDWARE variant is selected in the game
     * UI, and {@code false} when it is deselected.  Disabling immediately stops
     * any active injection and prevents new injection until re-enabled.
     *
     * @param enabled {@code true} to allow injection; {@code false} to stop and lock out
     */
    public void setInjectionEnabled(boolean enabled) {
        this.injectionEnabled = enabled;
        if (!enabled) stopInjectionOnly();
    }

    /**
     * Computes ball-to-paddle offset and sends the appropriate injection or
     * stop commands.  Only sends serial commands on state <em>transitions</em>
     * to avoid saturating the serial port with redundant messages.
     * No-op when {@link #injectionEnabled} is {@code false}.
     *
     * <p>Injection is suppressed when the ball is moving <em>away</em> from the
     * hardware player (i.e. {@code ballVelY >= 0}, ball heading toward the bottom).
     * Any active injection is stopped at that transition so the neurons are not
     * stimulated unnecessarily while the ball is out of play for this player.
     */
    private void updateInjection(PongState state) {
        if (!injectionEnabled) return;

        // Hardware player is always at the top; ball coming toward it has ballVelY < 0.
        // When the ball is moving away, stop any active injection and return.
        if (state.ballVelY() >= 0) {
            if (lastInject != InjectState.NONE) {
                injector.stopInjection(0);
                lastInject = InjectState.NONE;
            }
            return;
        }

        int paddleCenter = state.paddleX() + PongGame.PADDLE_WIDTH / 2;
        int diff         = state.ballX() - paddleCenter;

        if (diff < -DEAD_ZONE) {
            // Ball is to the LEFT — drive CH1 (left neurons)
            if (lastInject != InjectState.LEFT) {
                if (lastInject == InjectState.RIGHT) injector.stopInjection(2);
                injector.injectVoltage(1, NeuralHardwareConfig.PONG_CONSTANT_INJECT_VOLTAGE);
                lastInject = InjectState.LEFT;
            }
        } else if (diff > DEAD_ZONE) {
            // Ball is to the RIGHT — drive CH2 (right neurons)
            if (lastInject != InjectState.RIGHT) {
                if (lastInject == InjectState.LEFT) injector.stopInjection(1);
                injector.injectVoltage(2, NeuralHardwareConfig.PONG_CONSTANT_INJECT_VOLTAGE);
                lastInject = InjectState.RIGHT;
            }
        } else {
            // Within dead zone — stop all injection so the paddle coasts
            if (lastInject != InjectState.NONE) {
                injector.stopInjection(0);
                lastInject = InjectState.NONE;
            }
        }
    }

    /**
     * Stops any active injection without releasing the serial port.
     *
     * <p>Call this when the game window is closing but the
     * {@link HardwareSignalSource} is shared with other components (e.g. the
     * Launcher's {@link edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager}).
     */
    public void stopInjectionOnly() {
        injector.stopInjection(0);
        lastInject = InjectState.NONE;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Stops any active injection, then releases both underlying serial sources.
     * Register as a JVM shutdown hook so ports are always freed:
     * <pre>{@code
     * Runtime.getRuntime().addShutdownHook(new Thread(hwPlayer::close));
     * }</pre>
     */
    public void close() {
        stopInjectionOnly();
        if (leftSource  instanceof HardwareSignalSource h) h.close();
        if (rightSource instanceof HardwareSignalSource h) h.close();
    }
}
