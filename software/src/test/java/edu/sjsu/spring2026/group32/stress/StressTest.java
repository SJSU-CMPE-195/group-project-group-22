package edu.sjsu.spring2026.group32.stress;

import edu.sjsu.spring2026.group32.hardware.NeuralHardwareConfig;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.signal.HardwareSignalSource;
import edu.sjsu.spring2026.group32.hardware.signal.NeuralSignalParser;
import edu.sjsu.spring2026.group32.testsupport.SerialTestRig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Stress tests that focus on high-volume firmware traffic, disconnect/reconnect
 * churn, and hardware edge cases rather than pure microbenchmarks.
 *
 * <p>These tests intentionally exercise the fake firmware/device path so the
 * Java-side serial, signal, and hardware configuration code is stressed
 * without requiring physical ESP32 hardware.</p>
 */
@Tag("stress")
@DisplayName("Stress Suite")
class StressTest {
    private static final int MIXED_TRAFFIC_ITERATIONS = 750;
    private static final int RECOVERY_CYCLES = 10;
    private static final int SIGNAL_RECOVERY_CYCLES = 12;
    private static final long AWAIT_TIMEOUT_MS = 4_000L;
    private static final double SPIKE_THRESHOLD_VOLTS = 0.5;
    private static final List<ScenarioResult> SCENARIO_RESULTS = new ArrayList<>();
    private static long suiteStartNs;

    @BeforeAll
    static void beforeAll() {
        suiteStartNs = System.nanoTime();
        SCENARIO_RESULTS.clear();
    }

    @AfterAll
    static void afterAll() {
        long suiteElapsedMs = elapsedMillis(suiteStartNs);
        long passed = SCENARIO_RESULTS.stream().filter(result -> "PASS".equals(result.result())).count();
        long failed = SCENARIO_RESULTS.size() - passed;
        long scenarioSumMs = SCENARIO_RESULTS.stream().mapToLong(ScenarioResult::runtimeMs).sum();
        long overheadMs = suiteElapsedMs - scenarioSumMs;

        System.out.println();
        System.out.println("# Stress Test Results");
        System.out.println();
        System.out.println("### Test Configuration");
        System.out.println("- Tool: JUnit 5 stress suite with fake firmware/device support in `software/src/test/java/edu/sjsu/spring2026/group32/testsupport`");
        System.out.println("- Total Suite Runtime: " + suiteElapsedMs + " ms");
        System.out.println("- Virtual Users: Not applicable");
        System.out.println("- Target: Java hardware integration path");
        System.out.println("  - `SerialConnectionManager`");
        System.out.println("  - `HardwareSignalSource`");
        System.out.println("  - `NeuralSignalParser`");
        System.out.println("  - `NeuralHardwareConfig`");
        System.out.println("- Stress Scenarios:");
        System.out.println("  - Sustained mixed firmware traffic with valid samples, malformed lines, `STATUS`, `INFO?`, and injection commands");
        System.out.println("  - Random disconnect and reconnect recovery cycles");
        System.out.println("  - Signal burst, spike detection, and recovery after disconnect");
        System.out.println("  - Boundary and invalid-value sweeps for hardware configuration");
        System.out.println();
        System.out.println("### Results");
        System.out.println("| Metric | Value |");
        System.out.println("|--------|-------|");
        System.out.println("| Mixed Traffic Iterations | " + MIXED_TRAFFIC_ITERATIONS + " |");
        System.out.println("| Disconnect/Reconnect Cycles | " + RECOVERY_CYCLES + " |");
        System.out.println("| Signal Recovery Cycles | " + SIGNAL_RECOVERY_CYCLES + " |");
        System.out.println("| Passed Scenarios | " + passed + " |");
        System.out.println("| Failed Scenarios | " + failed + " |");
        System.out.println("| Total Stress Suite Runtime | " + suiteElapsedMs + " ms |");
        System.out.println();
        System.out.printf(
                Locale.US,
                "> The total suite runtime (%d ms) exceeds the sum of individual scenario runtimes (%d ms) by ~%d ms. "
                + "This is normal JUnit framework overhead — test class initialization, JVM warmup between test classes, and test runner bookkeeping.%n",
                suiteElapsedMs, scenarioSumMs, overheadMs);
        System.out.println();
        System.out.println("### Scenario Results");
        System.out.println("| Scenario | Result | Runtime | Notes |");
        System.out.println("|---|---|---|---|");
        for (ScenarioResult result : SCENARIO_RESULTS) {
            System.out.printf(
                    Locale.US,
                    "| `%s` | %s | %d ms | %s |%n",
                    result.name(),
                    result.result(),
                    result.runtimeMs(),
                    escapeTable(result.notes()));
        }
        System.out.println();
    }

    @Test
    @DisplayName("SerialConnectionManager survives sustained mixed firmware traffic with malformed lines")
    void serialConnectionManager_sustainedMixedFirmwareTraffic() {
        runScenario(
                "serialConnectionManager_sustainedMixedFirmwareTraffic",
                () -> "samples=" + MIXED_TRAFFIC_ITERATIONS + ", malformed injected under mixed command traffic",
                () -> {
                    SerialTestRig rig = SerialTestRig.createDualChannelRig();
                    AtomicInteger sampleCount = new AtomicInteger();
                    AtomicInteger statusCount = new AtomicInteger();
                    AtomicInteger infoCount = new AtomicInteger();

                    try {
                        rig.manager().addListener(new SerialConnectionManager.SerialListener() {
                            @Override
                            public void onSample(SerialConnectionManager.SampleFrame frame) {
                                sampleCount.incrementAndGet();
                            }

                            @Override
                            public void onStatusPayload(String payload) {
                                statusCount.incrementAndGet();
                            }

                            @Override
                            public void onInfoUpdated(String deviceName, int channelCount) {
                                infoCount.incrementAndGet();
                            }
                        });

                        assertTrue(rig.manager().connect(), "connect() should succeed with the fake ESP32 bridge");
                        awaitCondition(() -> rig.manager().getDeviceChannelCount() == 2, "initial firmware info handshake");

                        for (int i = 0; i < MIXED_TRAFFIC_ITERATIONS; i++) {
                            if (i % 25 == 0) {
                                rig.manager().sendLine("STATUS");
                            }
                            if (i % 40 == 0) {
                                rig.manager().sendLine("INFO?");
                            }
                            if (i % 120 == 0) {
                                rig.manager().sendLine("INJECT_V_CH1:1.250");
                            }
                            if (i % 120 == 60) {
                                rig.manager().sendLine("STOP_INJECT");
                            }
                            if (i % 33 == 0) {
                                rig.pushIncomingLine("bad,data," + i);
                            }
                            if (i % 47 == 0) {
                                rig.pushIncomingLine("100,0,0,NaN,NaN");
                            }

                            rig.pushIncomingLine(rig.firmware().sampleLine(
                                    50_000L + i,
                                    (i * 31) % 4096,
                                    (i * 73) % 4096));
                        }

                        awaitCondition(
                                () -> sampleCount.get() >= MIXED_TRAFFIC_ITERATIONS,
                                "all valid firmware samples to be consumed");

                        SerialConnectionManager.SampleFrame latest = rig.manager().getLatestSampleFrame();
                        assertNotNull(latest, "latest sample frame should be present after traffic burst");
                        assertEquals(50_000L + MIXED_TRAFFIC_ITERATIONS - 1, latest.millis(), "latest frame should come from the final valid sample");
                        assertEquals(((MIXED_TRAFFIC_ITERATIONS - 1) * 31) % 4096, latest.primaryRaw(), "primary channel should match the final generated sample");
                        assertEquals(((MIXED_TRAFFIC_ITERATIONS - 1) * 73) % 4096, latest.secondaryRaw(), "secondary channel should match the final generated sample");
                        assertTrue(statusCount.get() > 0, "STATUS payloads should still be delivered under mixed load");
                        assertTrue(infoCount.get() > 0, "INFO updates should still be delivered under mixed load");
                        assertTrue(rig.firmware().getReceivedCommands().contains("STATUS"), "firmware should record STATUS commands");
                        assertTrue(rig.firmware().getReceivedCommands().contains("INFO?"), "firmware should record INFO? commands");
                        assertTrue(rig.manager().isConnected(), "manager should remain connected throughout sustained traffic");
                    } finally {
                        rig.manager().disconnect();
                    }
                });
    }

    @Test
    @DisplayName("SerialConnectionManager recovers cleanly from repeated random disconnect and reconnect cycles")
    void serialConnectionManager_randomDisconnectReconnectCycles() {
        runScenario(
                "serialConnectionManager_randomDisconnectReconnectCycles",
                () -> "cycles=" + RECOVERY_CYCLES + ", seeded random burst traffic and full state reset checks",
                () -> {
                    SerialTestRig rig = SerialTestRig.createDualChannelRig();
                    Random random = new Random(20260505L);
                    AtomicInteger connectedCount = new AtomicInteger();
                    AtomicInteger disconnectedCount = new AtomicInteger();

                    try {
                        rig.manager().addListener(new SerialConnectionManager.SerialListener() {
                            @Override
                            public void onConnected(String portName) {
                                connectedCount.incrementAndGet();
                            }

                            @Override
                            public void onDisconnected(String reason) {
                                disconnectedCount.incrementAndGet();
                            }
                        });

                        assertTrue(rig.manager().connect(), "initial connect() should succeed");
                        awaitCondition(() -> rig.manager().getDeviceChannelCount() == 2, "initial dual-channel info");

                        for (int cycle = 0; cycle < RECOVERY_CYCLES; cycle++) {
                            int burstSize = 12 + random.nextInt(18);
                            long expectedMillis = -1L;

                            for (int i = 0; i < burstSize; i++) {
                                expectedMillis = (cycle * 10_000L) + i;
                                if (i % 5 == 0) {
                                    rig.pushIncomingLine("noise,line," + cycle + "," + i);
                                }
                                rig.pushIncomingLine(rig.firmware().sampleLine(
                                        expectedMillis,
                                        random.nextInt(4096),
                                        random.nextInt(4096)));
                            }

                            long finalExpectedMillis = expectedMillis;
                            awaitCondition(
                                    () -> rig.manager().getLatestSampleFrame() != null
                                            && rig.manager().getLatestSampleFrame().millis() == finalExpectedMillis,
                                    "last sample in cycle " + cycle + " to be processed");

                            rig.manager().disconnect();
                            int expectedDisconnects = cycle + 1;
                            awaitCondition(
                                    () -> disconnectedCount.get() >= expectedDisconnects,
                                    "disconnect listener for cycle " + cycle);

                            assertFalse(rig.manager().isConnected(), "manager should report disconnected after cycle teardown");
                            assertNull(rig.manager().getLatestSampleFrame(), "latest sample should be cleared after disconnect");
                            assertNull(rig.manager().getNextLine(), "latest line should be cleared after disconnect");
                            assertEquals("", rig.manager().getDeviceName(), "device name should be cleared after disconnect");
                            assertEquals(0, rig.manager().getDeviceChannelCount(), "channel count should be cleared after disconnect");

                            if (cycle < RECOVERY_CYCLES - 1) {
                                assertTrue(rig.manager().connect(), "reconnect should succeed in cycle " + cycle);
                                int expectedConnects = cycle + 2;
                                awaitCondition(
                                        () -> connectedCount.get() >= expectedConnects
                                                && rig.manager().getDeviceChannelCount() == 2,
                                        "reconnect handshake for cycle " + cycle);
                            }
                        }

                        assertEquals(RECOVERY_CYCLES, connectedCount.get(), "connected listener should fire once per successful connect");
                        assertEquals(RECOVERY_CYCLES, disconnectedCount.get(), "disconnected listener should fire once per disconnect");
                    } finally {
                        rig.manager().disconnect();
                    }
                });
    }

    @Test
    @DisplayName("HardwareSignalSource handles sample bursts, repeated spikes, and reconnect recovery")
    void hardwareSignalSource_burstAndRecoveryStress() {
        runScenario(
                "hardwareSignalSource_burstAndRecoveryStress",
                () -> "cycles=" + SIGNAL_RECOVERY_CYCLES + ", repeated spike plateaus with reconnect recovery",
                () -> {
                    SerialTestRig rig = SerialTestRig.createSingleChannelRig();
                    assertTrue(rig.manager().connect(), "single-channel rig should connect");
                    awaitCondition(() -> rig.manager().getDeviceChannelCount() == 1, "single-channel info");

                    HardwareSignalSource signalSource = new HardwareSignalSource(rig.manager(), new NeuralSignalParser());
                    int observedSpikeEvents = 0;

                    try {
                        for (int cycle = 0; cycle < SIGNAL_RECOVERY_CYCLES; cycle++) {
                            rig.pushIncomingLine(rig.firmware().sampleLine(100_000L + cycle * 10L, 0));
                            awaitCondition(() -> signalSource.getNextVoltage() <= 0.05, "baseline low voltage in cycle " + cycle);

                            signalSource.injectVoltage(1, 2.750);
                            signalSource.stopInjection(1);

                            rig.pushIncomingLine(rig.firmware().sampleLine(100_000L + cycle * 10L + 1L, 4095));
                            awaitCondition(() -> signalSource.getNextVoltage() >= 3.2, "high-voltage spike in cycle " + cycle);

                            assertTrue(signalSource.hasSpike(SPIKE_THRESHOLD_VOLTS), "first high reading should register a spike");
                            assertFalse(signalSource.hasSpike(SPIKE_THRESHOLD_VOLTS), "plateau should not register duplicate spikes");
                            observedSpikeEvents++;

                            rig.pushIncomingLine(rig.firmware().sampleLine(100_000L + cycle * 10L + 2L, 50));
                            awaitCondition(() -> signalSource.getNextVoltage() < SPIKE_THRESHOLD_VOLTS, "voltage to fall below threshold in cycle " + cycle);
                            assertFalse(
                                    signalSource.hasSpike(SPIKE_THRESHOLD_VOLTS),
                                    "low reading should clear rising-edge state without producing a spike");

                            if (cycle % 3 == 2 && cycle < SIGNAL_RECOVERY_CYCLES - 1) {
                                rig.manager().disconnect();
                                awaitCondition(() -> !rig.manager().isConnected(), "manager disconnect in cycle " + cycle);
                                assertEquals(0.0, signalSource.getNextVoltage(), 0.0001, "voltage should reset after disconnect");
                                assertFalse(signalSource.hasSpike(SPIKE_THRESHOLD_VOLTS), "disconnected source should not report spikes");

                                assertTrue(rig.manager().connect(), "reconnect should succeed after forced disconnect");
                                awaitCondition(() -> rig.manager().getDeviceChannelCount() == 1, "single-channel reconnect info");
                            }
                        }

                        assertEquals(SIGNAL_RECOVERY_CYCLES, observedSpikeEvents, "each cycle should produce exactly one observed spike event");
                        assertTrue(
                                rig.firmware().getReceivedCommands().stream().anyMatch(command -> command.startsWith("INJECT_V_CH1:2.750")),
                                "firmware should receive injection commands during stress run");
                        assertTrue(
                                rig.firmware().getReceivedCommands().contains("STOP_INJECT_CH1"),
                                "firmware should receive channel stop commands during stress run");
                    } finally {
                        signalSource.close();
                    }
                });
    }

    @Test
    @DisplayName("NeuralHardwareConfig accepts repeated boundary updates and rejects invalid values")
    void neuralHardwareConfig_boundaryAndFailureSweep() {
        runScenario(
                "neuralHardwareConfig_boundaryAndFailureSweep",
                () -> "25 boundary update rounds plus invalid voltage and speed rejection checks",
                () -> {
                    double originalHitTheZoneInjection = NeuralHardwareConfig.getHitTheZoneInjectionVoltage();
                    double originalHitTheZoneThreshold = NeuralHardwareConfig.getHitTheZoneThresholdVoltage();
                    double originalPongLeftInjection = NeuralHardwareConfig.getPongLeftInjectionVoltage();
                    double originalPongRightInjection = NeuralHardwareConfig.getPongRightInjectionVoltage();
                    double originalPongLeftThreshold = NeuralHardwareConfig.getPongLeftThresholdVoltage();
                    double originalPongRightThreshold = NeuralHardwareConfig.getPongRightThresholdVoltage();
                    int originalPaddleSpeed = NeuralHardwareConfig.getPongHardwarePaddleSpeed();

                    try {
                        for (int i = 0; i < 25; i++) {
                            double validVoltage = (i % 2 == 0) ? NeuralHardwareConfig.MIN_GAMEPLAY_VOLTAGE : NeuralHardwareConfig.MAX_GAMEPLAY_VOLTAGE;
                            int validSpeed = (i % 2 == 0)
                                    ? NeuralHardwareConfig.MIN_PONG_HARDWARE_PADDLE_SPEED
                                    : NeuralHardwareConfig.MAX_PONG_HARDWARE_PADDLE_SPEED;

                            NeuralHardwareConfig.setHitTheZoneInjectionVoltage(validVoltage);
                            NeuralHardwareConfig.setHitTheZoneThresholdVoltage(validVoltage);
                            NeuralHardwareConfig.setPongLeftInjectionVoltage(validVoltage);
                            NeuralHardwareConfig.setPongRightInjectionVoltage(validVoltage);
                            NeuralHardwareConfig.setPongLeftThresholdVoltage(validVoltage);
                            NeuralHardwareConfig.setPongRightThresholdVoltage(validVoltage);
                            NeuralHardwareConfig.setPongHardwarePaddleSpeed(validSpeed);

                            assertEquals(validVoltage, NeuralHardwareConfig.getHitTheZoneInjectionVoltage(), 0.0, "HTZ injection should match latest valid boundary value");
                            assertEquals(validVoltage, NeuralHardwareConfig.getHitTheZoneThresholdVoltage(), 0.0, "HTZ threshold should match latest valid boundary value");
                            assertEquals(validVoltage, NeuralHardwareConfig.getPongLeftInjectionVoltage(), 0.0, "Pong left injection should match latest valid boundary value");
                            assertEquals(validVoltage, NeuralHardwareConfig.getPongRightInjectionVoltage(), 0.0, "Pong right injection should match latest valid boundary value");
                            assertEquals(validVoltage, NeuralHardwareConfig.getPongLeftThresholdVoltage(), 0.0, "Pong left threshold should match latest valid boundary value");
                            assertEquals(validVoltage, NeuralHardwareConfig.getPongRightThresholdVoltage(), 0.0, "Pong right threshold should match latest valid boundary value");
                            assertEquals(validSpeed, NeuralHardwareConfig.getPongHardwarePaddleSpeed(), "paddle speed should match latest valid boundary value");
                        }

                        assertThrows(IllegalArgumentException.class, () -> NeuralHardwareConfig.setHitTheZoneInjectionVoltage(-0.001), "negative HTZ injection voltage should be rejected");
                        assertThrows(IllegalArgumentException.class, () -> NeuralHardwareConfig.setHitTheZoneThresholdVoltage(3.301), "HTZ threshold above max should be rejected");
                        assertThrows(IllegalArgumentException.class, () -> NeuralHardwareConfig.setPongLeftInjectionVoltage(-0.1), "negative Pong left injection voltage should be rejected");
                        assertThrows(IllegalArgumentException.class, () -> NeuralHardwareConfig.setPongRightInjectionVoltage(3.31), "Pong right injection voltage above max should be rejected");
                        assertThrows(IllegalArgumentException.class, () -> NeuralHardwareConfig.setPongLeftThresholdVoltage(-0.5), "negative Pong left threshold should be rejected");
                        assertThrows(IllegalArgumentException.class, () -> NeuralHardwareConfig.setPongRightThresholdVoltage(99.0), "Pong right threshold above max should be rejected");
                        assertThrows(IllegalArgumentException.class, () -> NeuralHardwareConfig.setPongHardwarePaddleSpeed(
                                NeuralHardwareConfig.MIN_PONG_HARDWARE_PADDLE_SPEED - 1), "paddle speed below minimum should be rejected");
                        assertThrows(IllegalArgumentException.class, () -> NeuralHardwareConfig.setPongHardwarePaddleSpeed(
                                NeuralHardwareConfig.MAX_PONG_HARDWARE_PADDLE_SPEED + 1), "paddle speed above maximum should be rejected");
                    } finally {
                        NeuralHardwareConfig.setHitTheZoneInjectionVoltage(originalHitTheZoneInjection);
                        NeuralHardwareConfig.setHitTheZoneThresholdVoltage(originalHitTheZoneThreshold);
                        NeuralHardwareConfig.setPongLeftInjectionVoltage(originalPongLeftInjection);
                        NeuralHardwareConfig.setPongRightInjectionVoltage(originalPongRightInjection);
                        NeuralHardwareConfig.setPongLeftThresholdVoltage(originalPongLeftThreshold);
                        NeuralHardwareConfig.setPongRightThresholdVoltage(originalPongRightThreshold);
                        NeuralHardwareConfig.setPongHardwarePaddleSpeed(originalPaddleSpeed);
                    }
                });
    }

    private static void runScenario(String scenarioName, NoteSupplier noteSupplier, ThrowingRunnable body) {
        long startNs = System.nanoTime();
        try {
            body.run();
            SCENARIO_RESULTS.add(new ScenarioResult(
                    scenarioName,
                    "PASS",
                    elapsedMillis(startNs),
                    noteSupplier.get()));
        } catch (Throwable throwable) {
            SCENARIO_RESULTS.add(new ScenarioResult(
                    scenarioName,
                    "FAIL",
                    elapsedMillis(startNs),
                    throwable.getClass().getSimpleName() + ": " + sanitize(throwable.getMessage())));
            throw rethrowUnchecked(throwable);
        }
    }

    private static void awaitCondition(BooleanSupplier condition, String description) {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for: " + description);
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for: " + description);
            }
        }
    }

    private static long elapsedMillis(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private static String escapeTable(String value) {
        return sanitize(value).replace("|", "\\|");
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace(System.lineSeparator(), " ").trim();
    }

    private static RuntimeException rethrowUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new RuntimeException(throwable);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface NoteSupplier {
        String get();
    }

    private record ScenarioResult(String name, String result, long runtimeMs, String notes) {
    }
}
