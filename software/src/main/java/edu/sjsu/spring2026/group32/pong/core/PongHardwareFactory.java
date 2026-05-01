package edu.sjsu.spring2026.group32.pong.core;

import edu.sjsu.spring2026.group32.hardware.NeuralHardwareConfig;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.signal.HardwareSignalSource;
import edu.sjsu.spring2026.group32.hardware.signal.NeuralSignalParser;
import edu.sjsu.spring2026.group32.pong.ai.PongHardwareAI;

/**
 * Creates hardware-backed Pong players from a live serial connection.
 */
public final class PongHardwareFactory {
    private PongHardwareFactory() {
    }

    public static PongHardwareAI createHardwarePlayer(SerialConnectionManager pongManager) {
        if (pongManager == null || !pongManager.isConnected() || pongManager.getDeviceChannelCount() < 2) {
            return null;
        }

        NeuralSignalParser leftParser = new NeuralSignalParser(0);
        NeuralSignalParser rightParser = new NeuralSignalParser(1);
        HardwareSignalSource leftSource = new HardwareSignalSource(pongManager, leftParser);
        HardwareSignalSource rightSource = new HardwareSignalSource(pongManager, rightParser);
        return new PongHardwareAI(
                "Hardware",
                leftSource,
                rightSource,
                leftSource,
                NeuralHardwareConfig.PONG_FIRING_THRESHOLD_VOLTS);
    }
}
