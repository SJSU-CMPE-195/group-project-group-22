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
     * CSV column index of the ADC reading to parse.
     * Channel 0 reads {@code parts[3]} (GPIO34, single-channel config).
     * Channel 1 reads {@code parts[4]} (GPIO35, dual-channel config).
     */
    private final int channelIndex;

    /**
     * Default constructor: channel 0, ESP32 12-bit ADC, 3.3 V, light smoothing.
     */
    public NeuralSignalParser() {
        this(4095.0, 3.3, 0.4, 0);
    }

    /**
     * Channel-selecting constructor: same ESP32 defaults, choose which ADC column to read.
     * @param channelIndex 0 for GPIO34 (single/dual-channel config 1),
     *                     1 for GPIO35 (dual-channel config 2 only)
     */
    public NeuralSignalParser(int channelIndex) {
        this(4095.0, 3.3, 0.4, channelIndex);
    }

    /**
     * Full flexibility constructor: any microcontroller, any channel.
     * @param maxAdcValue    e.g., 4095 for 12-bit, 1023 for 10-bit
     * @param systemVoltage  e.g., 3.3 for ESP32, 5.0 for Arduino Uno
     * @param smoothingFactor 1.0 = no smoothing, 0.1 = heavy smoothing
     * @param channelIndex   CSV column offset from index 3 (0 = parts[3], 1 = parts[4], …)
     */
    public NeuralSignalParser(double maxAdcValue, double systemVoltage,
                              double smoothingFactor, int channelIndex) {
        this.maxAdcValue     = maxAdcValue;
        this.systemVoltage   = systemVoltage;
        this.smoothingFactor = Math.max(0.0, Math.min(1.0, smoothingFactor));
        this.channelIndex    = Math.max(0, channelIndex);
    }

    public double parseVoltage(String line) {
        if (line == null || line.trim().isEmpty()) {
            return currentEmaVoltage; // Return the last known stable voltage if data drops temporarily
        }

        try {
            String[] parts = line.trim().split(",");
            int col = 3 + channelIndex;
            if (parts.length > col) {
                int rawVal = Integer.parseInt(parts[col]);

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