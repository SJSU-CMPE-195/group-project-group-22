package edu.sjsu.spring2026.group32.hardware;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;

public class HardwareSignalSource implements BaseSignalSource, VoltageInjector {
    /**
     * Opt-in trace for raw parsed-voltage serial samples.
     *
     * <p>Disabled by default because the shared serial listener can emit this
     * line at a very high rate in both Hit The Zone and Pong. Enable with:
     * {@code -Dgroup32.trace.hardware.parsedVoltage=true}
     */
    private static final boolean TRACE_PARSED_VOLTAGE =
            Boolean.getBoolean("group32.trace.hardware.parsedVoltage");
    private static final double LOGGED_VOLTAGE_MIN = 0.75;

    private final SerialConnectionManager connectionManager;
    private final NeuralSignalParser parser;
    private final SerialConnectionManager.SerialListener serialListener =
            new SerialConnectionManager.SerialListener() {
                @Override
                public void onConnected(String portName) {
                    seedFromLatestSample();
                }

                @Override
                public void onSample(SerialConnectionManager.SampleFrame frame) {
                    double voltage = parser.parseVoltage(frame.line());
                    latestVoltage = voltage;
                    if (TRACE_PARSED_VOLTAGE && voltage >= LOGGED_VOLTAGE_MIN) {
                        System.out.printf("[HTZ-HW] parsed voltage=%.3fV line=%s%n", voltage, frame.line());
                    }
                }

                @Override
                public void onDisconnected(String reason) {
                    latestVoltage = 0.0;
                    parser.resetFilter();
                }
            };

    private volatile double latestVoltage = 0.0;

    public HardwareSignalSource(SerialConnectionManager connectionManager, NeuralSignalParser parser) {
        this.connectionManager = connectionManager;
        this.parser = parser;
        this.connectionManager.addListener(serialListener);
        if (this.connectionManager.isConnected()) {
            seedFromLatestSample();
        }
    }

    @Override
    public double getNextVoltage() {
        if (!connectionManager.isConnected()) {
            return 0.0;
        }

        return latestVoltage;
    }

    /**
     * Delegates to {@link NeuralSignalParser#hasSpike(double)}, providing
     * stateful rising-edge detection: returns {@code true} exactly once per
     * low-to-high crossing regardless of how many game ticks the spike spans.
     *
     * <p>Returns {@code false} immediately when the hardware is disconnected,
     * matching the behavior of {@link #getNextVoltage()}.
     */
    @Override
    public boolean hasSpike(double threshold) {
        if (!connectionManager.isConnected()) return false;
        return parser.hasSpike(threshold);
    }

    private void seedFromLatestSample() {
        SerialConnectionManager.SampleFrame frame = connectionManager.getLatestSampleFrame();
        if (frame != null) {
            latestVoltage = parser.parseVoltage(frame.line());
        }
    }

    @Override
    public void injectVoltage(int channel, double volts) {
        validateChannel(channel);
        connectionManager.sendLine(String.format("INJECT_V_CH%d:%.3f", channel, volts));
    }

    @Override
    public void stopInjection(int channel) {
        if (channel == 0) {
            connectionManager.sendLine("STOP_INJECT");
            return;
        }

        validateChannel(channel);
        connectionManager.sendLine("STOP_INJECT_CH" + channel);
    }

    private static void validateChannel(int channel) {
        if (channel < 1) {
            throw new IllegalArgumentException("Channel must be >= 1");
        }
    }

    public void close() {
        connectionManager.removeListener(serialListener);
        connectionManager.disconnect();
    }
}
