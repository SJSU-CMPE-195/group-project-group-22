package edu.sjsu.spring2026.group32.hardware.signal;

import edu.sjsu.spring2026.group32.testsupport.SerialTestRig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HardwareSignalSource Suite")
class HardwareSignalSourceTest {
    private SerialTestRig rig;
    private HardwareSignalSource signalSource;

    @BeforeEach
    void setUp() {
        rig = SerialTestRig.createSingleChannelRig();
        rig.connectManagerToFakeDevice();
        awaitCondition(
            () -> rig.manager().getDeviceChannelCount() > 0, "reader thread to process boot sequence"
        );

        signalSource = new HardwareSignalSource(rig.manager(), new NeuralSignalParser());
    }

    @AfterEach
    void tearDown() {
        if (signalSource != null) {
            signalSource.close();
        }
    }

    @Test
    @DisplayName("getNextVoltage() reflects firmware samples when connected, returns 0.0 when disconnected")
    void voltageUpdates_followFirmwareSamples() {
        // (2047 / 4095) * 3.3 ~ 1.649V
        rig.pushIncomingLine(rig.firmware().sampleLine(123456L, 2047));
        awaitCondition(() -> signalSource.getNextVoltage() > 0.0, "voltage to update from sample");
        assertEquals(1.649, signalSource.getNextVoltage(), 0.01, "Voltage should reflect the latest parsed firmware sample");

        rig.device().simulateDisconnect();

        awaitCondition(() -> signalSource.getNextVoltage() == 0.0, "voltage to reset after disconnect");
        assertEquals(0.0, signalSource.getNextVoltage(), 0.0001, "Voltage should reset to 0.0 after disconnect");
    }

    @Test
    @DisplayName("hasSpike() returns false when disconnected")
    void hasSpike_returnsFalse_whenDisconnected() {
        rig.device().simulateDisconnect();

        awaitCondition(() -> !rig.manager().isConnected(), "manager to detect disconnect");
        assertFalse(signalSource.hasSpike(1.0), "hasSpike() must return false when hardware is not connected");
    }

    @Test
    @DisplayName("hasSpike() detects single rising-edge crossing when connected")
    void hasSpike_detectsRisingEdge_whenConnected() {
        rig.pushIncomingLine(rig.firmware().sampleLine(100000L, 0));

        awaitCondition(() -> signalSource.getNextVoltage() == 0.0,"below-threshold voltage to be written by listener");
        assertFalse(signalSource.hasSpike(1.0),"No spike yet — voltage is below threshold");

        // rising edge — ADC 3000 --> ~2.4V, crosses above 1.0V threshold
        rig.pushIncomingLine(rig.firmware().sampleLine(200000L, 3000));
        awaitCondition(() -> signalSource.getNextVoltage() > 1.0, "above-threshold voltage to be written by listener");
        assertTrue(signalSource.hasSpike(1.0), "Should detect spike on first call after rising-edge threshold crossing");

        // single firing — spike fires exactly once per crossing
        assertFalse(signalSource.hasSpike(1.0), "Second call above threshold should return false — single rising-edge detection");
    }

    @Test
    @DisplayName("injectVoltage() and stopInjection() send correct firmware commands, reject invalid channels")
    void injectionCommands_matchFirmwareProtocol() {
        signalSource.injectVoltage(1, 1.5);
        awaitCondition(() -> "INJECT_V_CH1:1.500".equals(rig.firmware().getLastReceivedCommand()), "firmware to receive inject command");

        int commandsBefore = rig.firmware().getReceivedCommands().size();
        signalSource.stopInjection(0);
        awaitCondition(() -> rig.firmware().getReceivedCommands().size() > commandsBefore && "STOP_INJECT".equals(rig.firmware().getLastReceivedCommand()), "firmware to receive global stop command");

        int commandsBefore2 = rig.firmware().getReceivedCommands().size();
        signalSource.stopInjection(1);
        awaitCondition(() -> rig.firmware().getReceivedCommands().size() > commandsBefore2 && "STOP_INJECT_CH1".equals(rig.firmware().getLastReceivedCommand()), "firmware to receive channel stop command");

        assertThrows(IllegalArgumentException.class, () -> signalSource.injectVoltage(0, 1.0), "Channel 0 is invalid for injectVoltage");
        assertThrows(IllegalArgumentException.class, () -> signalSource.injectVoltage(-1, 1.0), "Negative channel is invalid for injectVoltage");
        assertThrows(IllegalArgumentException.class, () -> signalSource.stopInjection(-1), "Negative channel is invalid for stopInjection");

    }

    /**
     * polls a condition up to 2 seconds in 20 ms slices.
     * gives the serial reader thread time to process async events
     */
    private static void awaitCondition(BooleanSupplier condition, String description) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for: " + description);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for: " + description);
            }
        }
    }
}