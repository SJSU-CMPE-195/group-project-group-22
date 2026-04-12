package edu.sjsu.spring2026.group32.hardware;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;

public class HardwareSignalSource implements BaseSignalSource {
    private final SerialConnectionManager connectionManager;
    private final NeuralSignalParser parser;

    // Cooldown variables for reconnection
    private long lastReconnectAttemptTime = 0;
    private static final long RECONNECT_COOLDOWN_MS = 2000; // Wait 2 seconds between attempts

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
            String rawLine = connectionManager.getNextLine();

            // If scanner returns null or throws an exception, it usually means the device was unplugged
            if (rawLine == null) {
                return 0.0;
            }
            return parser.parseVoltage(rawLine);

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

    public void close() {
        connectionManager.disconnect();
    }
}