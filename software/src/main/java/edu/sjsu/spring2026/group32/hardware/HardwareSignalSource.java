package edu.sjsu.spring2026.group32.hardware;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;

public class HardwareSignalSource implements BaseSignalSource, VoltageInjector {
    private static final double LOGGED_VOLTAGE_MIN = 0.75;
    private final SerialConnectionManager connectionManager;
    private final NeuralSignalParser parser;

    // Cooldown variables for reconnection
    private long lastReconnectAttemptTime = 0;
    private static final long RECONNECT_COOLDOWN_MS = 5000; // Wait X ms between attempts

    public HardwareSignalSource(SerialConnectionManager connectionManager, NeuralSignalParser parser) {
        this.connectionManager = connectionManager;
        this.parser = parser;
        // Only auto-connect if the port isn't already open.
        // When the Launcher provides a pre-connected manager (via connectTo()),
        // calling connect() again would attempt to reopen an already-open port.
        if (!this.connectionManager.isConnected()) {
            this.connectionManager.connect();
        }
    }

    @Override
    public double getNextVoltage() {
        // 1. Check if we lost the connection
        if (!connectionManager.isConnected()) {
            handleDisconnection();
            return 0.0; // Return safe default while disconnected
        }

        // 2. Try to read data
        try {
            String rawLine;

            // Skip firmware status/info lines (sent in response to inject/stop commands
            // and on boot). Passing them to NeuralSignalParser would cause a parse error
            // that would incorrectly disconnect the port.
            //   STATUS,INJECT_START,CH1,…   – inject acknowledgement
            //   STATUS,INJECT_STOPPED,CH1   – stop acknowledgement
            //   STATUS,BOOT,…               – startup message
            //   #INFO:…                     – capability announcement
            do {
                rawLine = connectionManager.getNextLine();
                if (rawLine == null) return 0.0;
            } while (rawLine.startsWith("STATUS") || rawLine.startsWith("#"));

            double voltage = parser.parseVoltage(rawLine);
            if (voltage >= LOGGED_VOLTAGE_MIN) {
                System.out.printf("[HTZ-HW] parsed voltage=%.3fV line=%s%n", voltage, rawLine);
            }
            return voltage;

        } catch (Exception e) {
            // The USB cable was violently pulled out mid-read
            System.err.println(">>> Connection lost abruptly. Closing port.");
            connectionManager.disconnect();
            return 0.0;
        }
    }

    private void handleDisconnection() {
        long currentTime = System.currentTimeMillis();

        // Only try to reconnect if 2 seconds have passed since the last attempt
        if (currentTime - lastReconnectAttemptTime > RECONNECT_COOLDOWN_MS) {
            System.out.println(">>> Attempting to reconnect to hardware...");
            lastReconnectAttemptTime = currentTime;

            // Because of the Supplier, this will dynamically rescan the USB ports!
            connectionManager.connect();
        }
    }

    // ---- VoltageInjector ----------------------------------------------------

    /**
     * Sends a NeuralSerial-compatible voltage injection command for the requested
     * 1-based channel.
     */
    @Override
    public void injectVoltage(int channel, double volts) {
        validateChannel(channel);
        connectionManager.sendLine(String.format("INJECT_V_CH%d:%.3f", channel, volts));
    }

    /**
     * Stops injection on a specific channel, or all channels when {@code channel}
     * is {@code 0}.
     */
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

    // ---- Lifecycle ----------------------------------------------------------

    public void close() {
        connectionManager.disconnect();
    }
}
