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

    private NeuralHardwareConfig() {
        // Utility class.
    }
}
