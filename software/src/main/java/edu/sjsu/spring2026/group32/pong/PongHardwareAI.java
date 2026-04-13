package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.HardwareSignalSource;
import edu.sjsu.spring2026.group32.player.BasePlayer;
import edu.sjsu.spring2026.group32.player.PlayerType;

/**
 * Hardware AI player for vertical Pong.
 *
 * Uses two ADC channels from the 6-neuron dual-channel firmware:
 *   Channel 0 (GPIO34) = leftSource  -- voltage spike =&gt; PongAction.LEFT
 *   Channel 1 (GPIO35) = rightSource -- voltage spike =&gt; PongAction.RIGHT
 *
 * If both channels fire simultaneously LEFT takes precedence.
 * If neither fires, returns PongAction.IDLE.
 *
 * This class implements BasePlayer directly rather than extending
 * HardwareAIPlayer because the parent wires only a single BaseSignalSource,
 * whereas this player requires two independent sources for bidirectional control.
 *
 * Wiring in Launcher:
 *   NeuralSignalParser p0 = new NeuralSignalParser(0);  // ch0 = GPIO34 = LEFT
 *   NeuralSignalParser p1 = new NeuralSignalParser(1);  // ch1 = GPIO35 = RIGHT
 *   BaseSignalSource leftSrc  = new HardwareSignalSource(pongManager, p0);
 *   BaseSignalSource rightSrc = new HardwareSignalSource(pongManager, p1);
 *   PongHardwareAI hw = new PongHardwareAI("Hardware", leftSrc, rightSrc);
 */
public class PongHardwareAI implements BasePlayer<PongState, PongAction> {

    /** Default firing threshold (volts). */
    private static final double DEFAULT_THRESHOLD = 2.0;

    private final String           name;
    private final BaseSignalSource leftSource;
    private final BaseSignalSource rightSource;
    private final double           threshold;

    /**
     * Full constructor.
     *
     * @param name        display name shown in scoreboard and overlays
     * @param leftSource  signal source for LEFT movement (channel 0 / GPIO34)
     * @param rightSource signal source for RIGHT movement (channel 1 / GPIO35)
     * @param threshold   voltage at or above which a direction fires (0.0-3.3 V)
     */
    public PongHardwareAI(String name,
                          BaseSignalSource leftSource,
                          BaseSignalSource rightSource,
                          double threshold) {
        this.name        = name;
        this.leftSource  = leftSource;
        this.rightSource = rightSource;
        this.threshold   = threshold;
    }

    /**
     * Convenience constructor using the default 2.0 V threshold.
     *
     * @param name        display name shown in scoreboard and overlays
     * @param leftSource  signal source for LEFT movement (channel 0 / GPIO34)
     * @param rightSource signal source for RIGHT movement (channel 1 / GPIO35)
     */
    public PongHardwareAI(String name,
                          BaseSignalSource leftSource,
                          BaseSignalSource rightSource) {
        this(name, leftSource, rightSource, DEFAULT_THRESHOLD);
    }

    /**
     * Reads both channels and maps to the appropriate action.
     * LEFT is checked first; if both channels fire simultaneously, LEFT wins.
     */
    @Override
    public PongAction getNextMove(PongState state) {
        if (leftSource.getNextVoltage()  >= threshold) return PongAction.LEFT;
        if (rightSource.getNextVoltage() >= threshold) return PongAction.RIGHT;
        return PongAction.IDLE;
    }

    @Override public String     getName() { return name;               }
    @Override public PlayerType getType() { return PlayerType.HARDWARE; }

    /**
     * Releases both underlying serial ports.
     * Register as a JVM shutdown hook so ports are always freed:
     *   Runtime.getRuntime().addShutdownHook(new Thread(hwPlayer::close));
     */
    public void close() {
        if (leftSource  instanceof HardwareSignalSource h) h.close();
        if (rightSource instanceof HardwareSignalSource h) h.close();
    }
}
