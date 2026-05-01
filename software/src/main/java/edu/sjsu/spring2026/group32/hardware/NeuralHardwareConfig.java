package edu.sjsu.spring2026.group32.hardware;

/**
 * Shared defaults for neural-hardware integration.
 */
public final class NeuralHardwareConfig {

    /**
     * Default voltage threshold used by hardware-backed game AIs to treat
     * an input as a firing event.
     */
    public static final double DEFAULT_FIRING_THRESHOLD_VOLTS = 0.5;

    /**
     * Constant voltage injected into the HTZ hardware while the ball is in the zone.
     */
    public static final double CONSTANT_INJECT_VOLTAGE = 3.0;

    /**
     * Constant voltage injected into the Pong hardware to drive the left or right
     * 3-neuron channel toward the ball.  Adjust during hardware testing to tune
     * how reliably the neurons fire each game tick.
     */
    public static final double PONG_CONSTANT_INJECT_VOLTAGE = 3.0;

    /**
     * Voltage threshold at or above which a Pong ADC reading is treated as a
     * neural spike (i.e. a LEFT or RIGHT action is registered).
     * Tune independently of {@link #DEFAULT_FIRING_THRESHOLD_VOLTS} to account
     * for the Pong neuron culture's baseline and noise floor.
     */
    public static final double PONG_FIRING_THRESHOLD_VOLTS = 0.5;

    /**
     * Pixels the hardware-controlled paddle moves per game tick when the
     * corresponding neuron fires.  Decoupled from the software {@code PADDLE_SPEED}
     * constant in {@code PongGame} so the hardware response can be tuned
     * independently without affecting human or software-AI paddle movement.
     */
    public static final int PONG_HARDWARE_PADDLE_SPEED = 15;

    private NeuralHardwareConfig() {
        // Utility class.
    }
}
