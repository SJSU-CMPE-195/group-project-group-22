package edu.sjsu.spring2026.group32.hardware.serial;

import edu.sjsu.spring2026.group32.testsupport.SerialTestRig;
import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SerialConnectionManager Suite")
class SerialConnectionManagerTest {
    private SerialTestRig rig;

    @BeforeEach
    void setUp() {
        rig = SerialTestRig.createDualChannelRig();
    }

    @AfterEach
    void tearDown() {
        rig.manager().disconnect();
    }

    @Test
    @DisplayName("connect() processes boot and info lines, exposes device name and channel count")
    void connect_readsFirmwareBootAndInfo() {
        rig.connectManagerToFakeDevice();

        // Boot sequence emits a #INFO line which the manager parses automatically
        awaitCondition(() -> rig.manager().getDeviceChannelCount() > 0, "manager to process boot info line");

        assertTrue(rig.manager().isConnected(), "manager should be connected after successful open");
        assertEquals("NeuralSignal", rig.manager().getDeviceName(), "device name should match firmware");
        assertEquals(2, rig.manager().getDeviceChannelCount(), "channel count should match dual-channel firmware");

        // Confirm INFO? handshake also works on demand
        boolean handshook = rig.manager().readInfoHandshake(40);
        assertTrue(handshook, "readInfoHandshake() should return true when firmware responds to INFO?");
        assertEquals("NeuralSignal", rig.manager().getDeviceName(), "device name should still be correct after explicit handshake");
        assertEquals(2, rig.manager().getDeviceChannelCount(), "channel count should still be correct after explicit handshake");
    }

    @Test
    @DisplayName("sample frames are parsed correctly from single and dual channel firmware lines")
    void sampleFrames_parseFromRealisticFirmwareLines() {
        rig.connectManagerToFakeDevice();
        awaitCondition(() -> rig.manager().getDeviceChannelCount() > 0, "boot sequence to be processed");

        // collect samples via listener
        List<SerialConnectionManager.SampleFrame> received = new ArrayList<>();
        List<String> receivedLines = new ArrayList<>();
        rig.manager().addListener(new SerialConnectionManager.SerialListener() {
            @Override
            public void onSample(SerialConnectionManager.SampleFrame frame) {
                received.add(frame);
            }

            @Override
            public void onSampleLine(String line) {
                receivedLines.add(line);
            }
        });

        // single-channel sample — millis=100000, ADC=2047
        String singleLine = rig.firmware().sampleLine(100000L, 2047);
        rig.pushIncomingLine(singleLine);
        awaitCondition(() -> rig.manager().getLatestSampleFrame() != null && rig.manager().getLatestSampleFrame().millis() == 100000L, "single-channel sample frame to be parsed");

        SerialConnectionManager.SampleFrame single = rig.manager().getLatestSampleFrame();
        assertEquals(100000L, single.millis(), "millis should match");
        assertEquals(2047, single.primaryRaw(), "primaryRaw should match ADC value");
        assertNull(single.secondaryRaw(), "secondaryRaw should be null for single-channel line");
        assertEquals(singleLine, rig.manager().getNextLine(), "getNextLine() should return the latest sample line");

        // dual-channel sample — millis=200000, ADC1=1000, ADC2=3000
        String dualLine = rig.firmware().sampleLine(200000L, 1000, 3000);
        rig.pushIncomingLine(dualLine);
        awaitCondition(() -> rig.manager().getLatestSampleFrame() != null && rig.manager().getLatestSampleFrame().millis() == 200000L, "dual-channel sample frame to be parsed");

        SerialConnectionManager.SampleFrame dual = rig.manager().getLatestSampleFrame();

        assertEquals(200000L, dual.millis(), "millis should match");
        assertEquals(1000, dual.primaryRaw(), "primaryRaw should match ADC1");

        assertNotNull(dual.secondaryRaw(), "secondaryRaw should be present for dual-channel line");
        assertEquals(3000, dual.secondaryRaw(), "secondaryRaw should match ADC2");

        // listen should have received both samples
        assertEquals(2, received.size(), "listener should have received both sample frames");
        assertEquals(2, receivedLines.size(), "listener should have received both sample lines");

        assertEquals(singleLine, receivedLines.get(0), "first listener line should match single sample");
        assertEquals(dualLine, receivedLines.get(1), "second listener line should match dual sample");
    }

    @Test
    @DisplayName("sendLine() reaches fake firmware and records commands; INFO? and STATUS produce replies")
    void sendLine_reachesFakeFirmwareAndRecordsCommands() {
        rig.connectManagerToFakeDevice();
        awaitCondition(() -> rig.manager().getDeviceChannelCount() > 0, "boot sequence to be processed");

        rig.manager().sendLine("INFO?");
        awaitCondition(() -> rig.firmware().getReceivedCommands().contains("INFO?"), "firmware to record INFO? command");
        assertTrue(rig.manager().readInfoHandshake(40), "INFO? should trigger a parseable info reply");

        List<String> statusPayloads = new ArrayList<>();
        rig.manager().addListener(new SerialConnectionManager.SerialListener() {
            @Override
            public void onStatusPayload(String payload) {
                statusPayloads.add(payload);
            }
            
        });

        int commandsBefore = rig.firmware().getReceivedCommands().size();
        rig.manager().sendLine("STATUS");

        awaitCondition(() -> rig.firmware().getReceivedCommands().size() > commandsBefore && rig.firmware().getLastReceivedCommand().equals("STATUS"), "firmware to record STATUS command");
        awaitCondition(() -> !statusPayloads.isEmpty(), "listener to receive STATUS payload");

        assertTrue(statusPayloads.get(0).startsWith("OK,CH=2"), "STATUS reply payload should start with OK,CH=2");

        int commandsBefore2 = rig.firmware().getReceivedCommands().size();
        rig.manager().sendLine("INJECT_V_CH1:1.500");

        awaitCondition(() -> rig.firmware().getReceivedCommands().size() > commandsBefore2 && rig.firmware().getLastReceivedCommand().equals("INJECT_V_CH1:1.500"), "firmware to record inject command");

        int commandsBefore3 = rig.firmware().getReceivedCommands().size();
        rig.manager().sendLine("STOP_INJECT");

        awaitCondition(() -> rig.firmware().getReceivedCommands().size() > commandsBefore3 && rig.firmware().getLastReceivedCommand().equals("STOP_INJECT"), "firmware to record STOP_INJECT command");

        int commandsBefore4 = rig.firmware().getReceivedCommands().size();
        rig.manager().sendLine("INVALID_CMD");

        awaitCondition(() -> rig.firmware().getReceivedCommands().size() > commandsBefore4 && rig.firmware().getLastReceivedCommand().equals("INVALID_CMD"), "firmware to record unknown command");
    }

    @Test
    @DisplayName("disconnect and reconnect reset internal state and notify listeners correctly")
    void disconnectAndReconnect_handleLifecycleTransitions() {
        rig.connectManagerToFakeDevice();
        awaitCondition(() -> rig.manager().getDeviceChannelCount() > 0, "boot sequence to be processed");

        List<String> connectedPorts = new ArrayList<>();
        List<String> disconnectReasons = new ArrayList<>();
        rig.manager().addListener(new SerialConnectionManager.SerialListener() {
            @Override
            public void onConnected(String portName) {
                connectedPorts.add(portName);
            }

            @Override
            public void onDisconnected(String reason) {
                disconnectReasons.add(reason);
            }

        });


        rig.pushIncomingLine(rig.firmware().sampleLine(100000L, 2047));
        awaitCondition(() -> rig.manager().getLatestSampleFrame() != null, "sample frame to be present before disconnect");

        // first disconnect — verify internal state is fully cleared
        rig.manager().disconnect();
        awaitCondition(() -> !disconnectReasons.isEmpty(),"disconnect listener to fire");
        assertFalse(rig.manager().isConnected(), "manager should report disconnected");
        assertNull(rig.manager().getLatestSampleFrame(), "latestSampleFrame should be cleared after disconnect");
        assertNull(rig.manager().getNextLine(), "latestDataLine should be cleared after disconnect");
        assertEquals("", rig.manager().getDeviceName(), "deviceName should be cleared after disconnect");
        assertEquals(0, rig.manager().getDeviceChannelCount(), "deviceChannelCount should be cleared after disconnect");

        // reconnect — boot sequence re-runs, state is restored
        rig.connectManagerToFakeDevice();
        awaitCondition(() -> rig.manager().getDeviceChannelCount() > 0, "reconnect boot sequence to be processed");
        assertTrue(rig.manager().isConnected(), "manager should be connected after reconnect");
        assertEquals("NeuralSignal", rig.manager().getDeviceName(), "device name should be restored after reconnect");
        assertEquals(2, rig.manager().getDeviceChannelCount(), "channel count should be restored after reconnect");
        assertFalse(connectedPorts.isEmpty(), "onConnected listener should have fired on reconnect");

        // second disconnect — verify the cycle can repeat cleanly
        rig.manager().disconnect();
        awaitCondition(() -> disconnectReasons.size() >= 2, "second disconnect listener to fire");
        assertFalse(rig.manager().isConnected(), "manager should be disconnected after second disconnect");
        assertEquals("", rig.manager().getDeviceName(), "deviceName should be cleared after second disconnect");
        assertEquals(0, rig.manager().getDeviceChannelCount(), "channelCount should be cleared after second disconnect");
    }

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