package edu.sjsu.spring2026.group32.hardware;

public class NeuralSignalParser {
    // Hardware configuration
    private final double maxAdcValue;
    private final double systemVoltage;

    // EMA Filter configuration and state
    private final double smoothingFactor; // Alpha (0.0 to 1.0)
    private double currentEmaVoltage = 0.0;
    private boolean isFirstReading = true;

    /**
     * Default constructor: Tuned for ESP32 (12-bit ADC, 3.3V) with light smoothing.
     */
    public NeuralSignalParser() {
        this(4095.0, 3.3, 0.4); // 0.4 alpha provides a good balance of smooth vs responsive
    }

    /**
     * Ultimate flexibility constructor: Works with any microcontroller.
     * @param maxAdcValue e.g., 4095 for 12-bit, 1023 for 10-bit
     * @param systemVoltage e.g., 3.3 for ESP32, 5.0 for Arduino Uno
     * @param smoothingFactor 1.0 = no smoothing, 0.1 = heavy smoothing
     */
    public NeuralSignalParser(double maxAdcValue, double systemVoltage, double smoothingFactor) {
        this.maxAdcValue = maxAdcValue;
        this.systemVoltage = systemVoltage;
        this.smoothingFactor = Math.max(0.0, Math.min(1.0, smoothingFactor)); // Clamp between 0 and 1
    }

    public double parseVoltage(String line) {
        if (line == null || line.trim().isEmpty()) {
            return currentEmaVoltage; // Return the last known stable voltage if data drops temporarily
        }

        try {
            String[] parts = line.trim().split(",");
            if (parts.length >= 4) {
                int rawVal = Integer.parseInt(parts[3]);

                // Calculate the raw, instantaneous voltage
                double rawVoltage = (rawVal / maxAdcValue) * systemVoltage;

                // Apply the EMA Filter
                if (isFirstReading) {
                    currentEmaVoltage = rawVoltage; // Seed the filter instantly on first read
                    isFirstReading = false;
                } else {
                    currentEmaVoltage = (rawVoltage * smoothingFactor) + (currentEmaVoltage * (1.0 - smoothingFactor));
                }

                return currentEmaVoltage;
            }
        } catch (NumberFormatException e) {
            // Malformed data (like half-written serial lines).
            // Just ignore and return the smoothed historical value.
        }

        return currentEmaVoltage;
    }

    // Optional: A way to reset the filter if the hardware reconnects
    public void resetFilter() {
        isFirstReading = true;
        currentEmaVoltage = 0.0;
    }
}