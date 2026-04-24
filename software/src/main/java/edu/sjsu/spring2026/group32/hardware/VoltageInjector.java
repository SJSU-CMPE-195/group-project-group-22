package edu.sjsu.spring2026.group32.hardware;

/**
 * Abstraction over the ESP32 voltage-injection protocol.
 *
 * <p>A {@link HardwareSignalSource} that holds a live serial connection
 * implements this interface to send NeuralSerial-compatible
 * {@code INJECT_V_CHn} / {@code STOP_INJECT_CHn} commands. Game-specific AI
 * players (e.g. {@code HitTheZoneHardwareAI}) accept a {@code VoltageInjector}
 * so they can stimulate the hardware at the right moment without needing
 * direct access to the serial layer.
 *
 * <p>Use {@link #NONE} anywhere no real injection is needed (software-only
 * runs, unit tests). It is a safe no-op that keeps callers free of null
 * checks.
 */
public interface VoltageInjector {

    /**
     * Sends a constant-voltage injection command to channel 1.
     *
     * @param volts the target voltage (0.0-3.3 V)
     */
    default void injectVoltage(double volts) {
        injectVoltage(1, volts);
    }

    /**
     * Sends a constant-voltage injection command to the selected channel.
     *
     * @param channel the 1-based NeuralSerial channel number
     * @param volts the target voltage (0.0-3.3 V)
     */
    void injectVoltage(int channel, double volts);

    /**
     * Stops injection on all channels and reverts the hardware to normal ADC sampling.
     */
    default void stopInjection() {
        stopInjection(0);
    }

    /**
     * Stops injection on the selected channel.
     *
     * <p>Passing {@code 0} stops all channels via {@code STOP_INJECT}, matching
     * the firmware protocol.
     *
     * @param channel the 1-based NeuralSerial channel number, or {@code 0} for all
     */
    void stopInjection(int channel);

    /**
     * No-op {@link VoltageInjector} for software-only runs and unit tests.
     * All method calls are silently ignored.
     */
    VoltageInjector NONE = new VoltageInjector() {
        @Override public void injectVoltage(int channel, double volts) {}
        @Override public void stopInjection(int channel) {}
    };
}
