package edu.sjsu.spring2026.group32.stress;

import edu.sjsu.spring2026.group32.hardware.signal.NeuralSignalParser;
import edu.sjsu.spring2026.group32.pong.ai.PongSoftwareAI;
import edu.sjsu.spring2026.group32.pong.model.PongState;
import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneSoftwareAI;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Throughput / stress tests for the core game-logic and signal-processing components.
 *
 * <p>These tests do not exercise Swing UI or serial hardware. Run them with:
 * <pre>
 *   mvn test -Dgroups=stress -pl software
 * </pre>
 *
 * <p>Results are documented in {@code docs/evaluation/stress-test-results.md}.
 *
 * <p>Design notes:
 * <ul>
 *   <li>A warm-up pass runs before the timed loop to let the JIT compiler
 *       optimise the hot path before measurement begins.</li>
 *   <li>The minimum throughput thresholds are deliberately conservative
 *       (100 k ops/s) — far below what any modern JVM achieves — so the
 *       assertions guard against catastrophic regressions rather than
 *       absolute performance targets.</li>
 * </ul>
 */
@Tag("stress")
class StressTest {

    /** Iterations used to warm up the JIT before timing begins. */
    private static final int WARMUP = 10_000;

    /** Iterations timed for the benchmark measurement. */
    private static final int ITERATIONS = 1_000_000;

    /**
     * Minimum acceptable throughput for any benchmark (ops/second).
     * 100 000 ops/s is orders of magnitude below realistic JVM performance
     * and is intentionally set low to catch only catastrophic regressions.
     */
    private static final long MIN_OPS_PER_SEC = 100_000L;

    // ── Cached state objects (allocated once, reused across loop iterations) ─

    private static final HitTheZoneState IN_ZONE  = new HitTheZoneState(true);
    private static final HitTheZoneState OUT_ZONE = new HitTheZoneState(false);

    // ── 1. HitTheZone AI throughput ──────────────────────────────────────────

    /**
     * Measures how many AI decisions per second {@link HitTheZoneSoftwareAI}
     * can produce when the ball alternates between in-zone and out-of-zone
     * states (3 ticks in zone, 7 ticks out — roughly a 30 % duty cycle).
     */
    @Test
    void hitTheZoneAI_decisionThroughput() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("StressBotHTZ", 0);

        // Warm-up
        for (int i = 0; i < WARMUP; i++) {
            ai.getNextMove(i % 10 < 3 ? IN_ZONE : OUT_ZONE);
        }

        // Timed benchmark
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            ai.getNextMove(i % 10 < 3 ? IN_ZONE : OUT_ZONE);
        }
        long elapsedNs = System.nanoTime() - start;

        long opsPerSec = (long) ((ITERATIONS * 1e9) / elapsedNs);
        System.out.printf(
            "[StressTest] HitTheZone AI : %,d decisions/sec  (elapsed %.1f ms)%n",
            opsPerSec, elapsedNs / 1e6);

        assertTrue(opsPerSec >= MIN_OPS_PER_SEC,
            "HitTheZone AI throughput too low: " + opsPerSec + " ops/s < minimum " + MIN_OPS_PER_SEC);
    }

    /**
     * Stress-tests the AI under maximum jitter ({@code maxJitterTicks=5}) to
     * confirm that non-deterministic branching does not degrade throughput.
     */
    @Test
    void hitTheZoneAI_highJitterThroughput() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("StressBotJitter", 5);

        for (int i = 0; i < WARMUP; i++) {
            ai.getNextMove(i % 10 < 3 ? IN_ZONE : OUT_ZONE);
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            ai.getNextMove(i % 10 < 3 ? IN_ZONE : OUT_ZONE);
        }
        long elapsedNs = System.nanoTime() - start;

        long opsPerSec = (long) ((ITERATIONS * 1e9) / elapsedNs);
        System.out.printf(
            "[StressTest] HitTheZone AI (jitter=5) : %,d decisions/sec  (elapsed %.1f ms)%n",
            opsPerSec, elapsedNs / 1e6);

        assertTrue(opsPerSec >= MIN_OPS_PER_SEC,
            "HitTheZone AI (high jitter) throughput too low: " + opsPerSec);
    }

    // ── 2. Pong AI throughput ────────────────────────────────────────────────

    /**
     * Benchmarks {@link PongSoftwareAI} (hard preset: deadZone=8, prob=1.0) by
     * cycling the ball X across the full field width each iteration.
     */
    @Test
    void pongSoftwareAI_decisionThroughput() {
        PongSoftwareAI ai = new PongSoftwareAI("StressBotPong", 8, 1.0);
        int fieldWidth = 800;
        int paddleX    = 360;

        // Warm-up
        for (int i = 0; i < WARMUP; i++) {
            ai.getNextMove(new PongState(paddleX, i % fieldWidth, 400, fieldWidth, 0));
        }

        // Timed benchmark — pre-allocate state objects to avoid GC noise
        PongState[] states = new PongState[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            states[i] = new PongState(paddleX, i % fieldWidth, 300 + (i % 200), fieldWidth, 0);
        }

        long start = System.nanoTime();
        for (PongState s : states) {
            ai.getNextMove(s);
        }
        long elapsedNs = System.nanoTime() - start;

        long opsPerSec = (long) ((ITERATIONS * 1e9) / elapsedNs);
        System.out.printf(
            "[StressTest] Pong AI (hard)  : %,d decisions/sec  (elapsed %.1f ms)%n",
            opsPerSec, elapsedNs / 1e6);

        assertTrue(opsPerSec >= MIN_OPS_PER_SEC,
            "Pong AI throughput too low: " + opsPerSec);
    }

    // ── 3. NeuralSignalParser throughput ─────────────────────────────────────

    /**
     * Measures {@link NeuralSignalParser#parseVoltage} throughput by feeding it
     * pre-generated CSV strings that sweep the full 12-bit ADC range (0–4095).
     *
     * <p>Input strings are pre-generated before timing begins so we measure
     * parser performance rather than string formatting overhead.
     */
    @Test
    void neuralSignalParser_parseThroughput() {
        NeuralSignalParser parser = new NeuralSignalParser();

        // Pre-generate test data (4096 unique ADC values, cycled over ITERATIONS)
        int sample = 4096;
        String[] lines = new String[sample];
        for (int i = 0; i < sample; i++) {
            lines[i] = "1609459200000,0,0," + i;
        }

        // Warm-up
        for (int i = 0; i < WARMUP; i++) {
            parser.parseVoltage(lines[i % sample]);
        }
        parser.resetFilter();

        // Timed benchmark
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            parser.parseVoltage(lines[i % sample]);
        }
        long elapsedNs = System.nanoTime() - start;

        long opsPerSec = (long) ((ITERATIONS * 1e9) / elapsedNs);
        System.out.printf(
            "[StressTest] NeuralSignalParser : %,d parses/sec  (elapsed %.1f ms)%n",
            opsPerSec, elapsedNs / 1e6);

        assertTrue(opsPerSec >= MIN_OPS_PER_SEC,
            "NeuralSignalParser throughput too low: " + opsPerSec);
    }

    /**
     * Confirms the parser degrades gracefully under malformed input rather than
     * throwing or hanging. Throughput should still exceed the minimum threshold.
     */
    @Test
    void neuralSignalParser_malformedInputThroughput() {
        NeuralSignalParser parser = new NeuralSignalParser();

        String[] malformed = {
            "",
            "   ",
            "bad,data",
            "1609459200000,0,0,NaN",
            "1609459200000,0,0,",
            "1609459200000,0,0,99999"   // out-of-range ADC value (still parseable)
        };

        for (int i = 0; i < WARMUP; i++) {
            parser.parseVoltage(malformed[i % malformed.length]);
        }
        parser.resetFilter();

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            parser.parseVoltage(malformed[i % malformed.length]);
        }
        long elapsedNs = System.nanoTime() - start;

        long opsPerSec = (long) ((ITERATIONS * 1e9) / elapsedNs);
        System.out.printf(
            "[StressTest] NeuralSignalParser (malformed) : %,d parses/sec  (elapsed %.1f ms)%n",
            opsPerSec, elapsedNs / 1e6);

        assertTrue(opsPerSec >= MIN_OPS_PER_SEC,
            "Parser malformed-input throughput too low: " + opsPerSec);
    }
}
