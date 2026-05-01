package edu.sjsu.spring2026.group32.hitthezone.core;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.signal.HardwareSignalSource;
import edu.sjsu.spring2026.group32.hardware.signal.NeuralSignalParser;
import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneHardwareAI;

/**
 * Creates hardware-backed Hit The Zone players.
 */
public final class HitTheZoneHardwareFactory {
    private static final String HARDWARE_PLAYER_NAME = "Neural";

    private HitTheZoneHardwareFactory() {
    }

    public static HitTheZoneHardwareAI createHardwarePlayer(SerialConnectionManager htzManager) {
        NeuralSignalParser parser = new NeuralSignalParser(0);
        HardwareSignalSource source = new HardwareSignalSource(htzManager, parser);
        return new HitTheZoneHardwareAI(HARDWARE_PLAYER_NAME, source, source);
    }
}
