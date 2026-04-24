package edu.sjsu.spring2026.group32.hardware;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;

public class HardwareSignalSource implements BaseSignalSource, VoltageInjector {
    private static final double LOGGED_VOLTAGE_MIN = 0.75;
    private static final long RECONNECT_COOLDOWN_MS = 5000;

    private final SerialConnectionManager connectionManager;
    private final NeuralSignalParser parser;
    private final SerialConnectionManager.SerialListener serialListener =
            new SerialConnectionManager.SerialListener() {
                @Override
                public void onSample(SerialConnectionManager.SampleFrame frame) {
                    double voltage = parser.parseVoltage(frame.line());
                    latestVoltage = voltage;
                    if (voltage >= LOGGED_VOLTAGE_MIN) {
                        System.out.printf("[HTZ-HW] parsed voltage=%.3fV line=%s%n", voltage, frame.line());
                    }
                }

                @Override
                public void onDisconnected(String reason) {
                    latestVoltage = 0.0;
                    parser.resetFilter();
                }
            };

    private long lastReconnectAttemptTime = 0;
    private volatile double latestVoltage = 0.0;

    public HardwareSignalSource(SerialConnectionManager connectionManager, NeuralSignalParser parser) {
        this.connectionManager = connectionManager;
        this.parser = parser;
        this.connectionManager.addListener(serialListener);
        if (!this.connectionManager.isConnected()) {
            this.connectionManager.connect();
        } else {
            seedFromLatestSample();
        }
    }

    @Override
    public double getNextVoltage() {
        if (!connectionManager.isConnected()) {
            handleDisconnection();
            return 0.0;
        }

        return latestVoltage;
    }

    private void handleDisconnection() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastReconnectAttemptTime > RECONNECT_COOLDOWN_MS) {
            System.out.println(">>> Attempting to reconnect to hardware...");
            lastReconnectAttemptTime = currentTime;
            connectionManager.connect();
            seedFromLatestSample();
        }
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
