package edu.sjsu.spring2026.group32.hitthezone;

import edu.sjsu.spring2026.group32.hardware.signal.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.signal.NeuralSignalParser;
import edu.sjsu.spring2026.group32.hardware.signal.VoltageInjector;
import edu.sjsu.spring2026.group32.player.model.PlayerType;
import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneHardwareAI;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HitTheZoneHardwareAI}.
 *
 * <p>{@link BaseSignalSource} is a SAM interface, so lambdas serve as
 * lightweight stubs — no mocking framework required.
 *
 * <h3>Key contracts verified here</h3>
 * <ul>
 *   <li>Rising-edge detection: SCORE is emitted only on the first tick voltage
 *       crosses the threshold from below while in-zone.  Subsequent held-high
 *       ticks return {@code null} because {@link NeuralSignalParser#hasSpike(double)}
 *       in the parser layer suppresses the plateau.  The {@code scoresOncePerThresholdCrossing}
 *       test uses a parser-backed source to exercise this full-stack behaviour.</li>
 *   <li>Out-of-zone scoring: when the ball is outside the zone and voltage ≥
 *       threshold the source intentionally returns SCORE (late-fire after zone
 *       exit is treated as a valid hit by the game).  This path is stateless by design.</li>
 *   <li>Zone transition side-effects: injector is called on entry/exit edges.</li>
 * </ul>
 */
@DisplayName("HitTheZoneHardwareAI Suite")
class HitTheZoneGameHardwareAITest {

    private static BaseSignalSource fixed(double voltage) {
        return () -> voltage;
    }

    /**
     * Builds a {@link BaseSignalSource} backed by a real {@link NeuralSignalParser}
     * so that {@link BaseSignalSource#hasSpike(double)} uses stateful rising-edge
     * detection rather than the stateless lambda default.
     *
     * <p>The caller pre-feeds the parser with a baseline sample (0 V) so that
     * {@code lastWasAbove} starts {@code false}, then returns the source.
     * Subsequent calls to {@link NeuralSignalParser#parseVoltage} on the returned
     * parser advance the internal voltage seen by {@code hasSpike()}.
     */
    private static Object[] parserBacked() {
        NeuralSignalParser parser = new NeuralSignalParser();
        parser.parseVoltage("0,0,0,0"); // seed baseline so lastWasAbove = false
        BaseSignalSource src = new BaseSignalSource() {
            @Override public double getNextVoltage() { return 0.0; } // not used by HTZ in-zone path
            @Override public boolean hasSpike(double threshold) { return parser.hasSpike(threshold); }
        };
        return new Object[]{parser, src};
    }

    // ── Basic scoring ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns SCORE when in zone and voltage meets threshold")
    void scoreWhenInZoneAndVoltageHigh() {
        HitTheZoneHardwareAI player = new HitTheZoneHardwareAI("bot", fixed(2.0), 1.0);
        assertEquals(HitTheZoneAction.SCORE, player.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Returns null when in zone but voltage is below threshold")
    void noScoreWhenVoltageLow() {
        HitTheZoneHardwareAI player = new HitTheZoneHardwareAI("Bot", fixed(0.5), 1.0);
        assertNull(player.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Returns SCORE at exactly the threshold voltage (inclusive)")
    void scoreAtExactThreshold() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(1.0), 1.0);
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Returns null just below the threshold voltage")
    void noScoreJustBelowThreshold() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(0.99), 1.0);
        assertNull(p.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Custom threshold is respected: voltage below custom threshold yields null")
    void customThresholdRespected() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 2.5);
        assertNull(p.getNextMove(new HitTheZoneState(true)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ── Out-of-zone scoring contract ──────────────────────────────────────────
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns SCORE when outside zone and a genuine rising-edge spike is present (late-fire contract)")
    void scoreWhenOutsideZoneAndVoltageHigh() {
        // The out-of-zone branch now uses sourceHasSpike() (rising-edge detection).
        // For a stateless lambda stub hasSpike() delegates to getNextVoltage() >= threshold,
        // so fixed(2.0) still returns SCORE — the stateless default models a fresh spike each call.
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 1.0);
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(false)));
    }

    @Test
    @DisplayName("Returns null when outside zone and voltage is below threshold")
    void noScoreWhenOutsideZoneAndVoltageLow() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(0.4), 1.0);
        assertNull(p.getNextMove(new HitTheZoneState(false)));
    }

    @Test
    @DisplayName("Out-of-zone plateau suppressed: in-zone spike does not produce a second SCORE on zone exit")
    void outOfZonePlateauSuppressedAfterInZoneSpike() {
        // Root-cause regression test for the double-attempt bug:
        // Before the fix, actionFromVoltage() used a stateless voltage >= threshold check
        // in the out-of-zone branch.  When the ball exited while voltage was still high
        // from the same spike that was already counted in-zone, a spurious SCORE was
        // returned, incrementing attempts without crediting a hit — halving accuracy.
        //
        // The fix: both branches now call sourceHasSpike() so the parser's lastWasAbove
        // latch suppresses the plateau regardless of whether the ball is in- or out-of-zone.
        Object[] parserAndSource = parserBacked();
        NeuralSignalParser parser = (NeuralSignalParser) parserAndSource[0];
        BaseSignalSource   src    = (BaseSignalSource)   parserAndSource[1];

        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", src, 1.0);

        // Rising edge fires while ball is in zone → exactly one SCORE
        parser.parseVoltage("0,0,0,4095");
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(true)),
                "in-zone rising edge → SCORE");

        // Ball exits zone; voltage is still high (plateau from the same spike) →
        // the out-of-zone branch must suppress this, not produce another SCORE
        parser.parseVoltage("0,0,0,4095");
        assertNull(p.getNextMove(new HitTheZoneState(false)),
                "out-of-zone plateau → null (no spurious second SCORE)");

        // Voltage drops; still out-of-zone → null
        parser.parseVoltage("0,0,0,0");
        assertNull(p.getNextMove(new HitTheZoneState(false)),
                "out-of-zone, voltage low → null");

        // A genuine NEW spike fires after zone exit (late-fire) → SCORE
        parser.parseVoltage("0,0,0,4095");
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(false)),
                "out-of-zone genuine new rising edge → SCORE (late-fire contract)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Rising-edge detection (NeuralSignalParser.hasSpike) ──────────────────
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scores once per threshold crossing: parser rising-edge suppresses duplicate on held-high plateau")
    void scoresOncePerThresholdCrossing() {
        // Use a NeuralSignalParser-backed source so hasSpike() performs real
        // rising-edge detection instead of the stateless lambda default.
        // This exercises the full stack: parser → BaseSignalSource → HardwareAIPlayer → HTZ AI.
        Object[] parserAndSource = parserBacked();
        NeuralSignalParser parser = (NeuralSignalParser) parserAndSource[0];
        BaseSignalSource src      = (BaseSignalSource)   parserAndSource[1];

        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", src, 1.0);
        HitTheZoneState inZone = new HitTheZoneState(true);

        // Feed a high-voltage sample (3.3 V) → rising edge
        parser.parseVoltage("0,0,0,4095");
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(inZone),
                "tick 1: rising edge → SCORE");

        // Voltage stays high (same line) → plateau must be suppressed
        parser.parseVoltage("0,0,0,4095");
        assertNull(p.getNextMove(inZone),
                "tick 2: voltage still high — parser's lastWasAbove suppresses second SCORE");

        // Plateau suppressed for a third tick too
        parser.parseVoltage("0,0,0,4095");
        assertNull(p.getNextMove(inZone),
                "tick 3: still high — no SCORE");

        // Voltage returns to baseline, then a fresh spike produces a new SCORE
        parser.parseVoltage("0,0,0,0");
        p.getNextMove(inZone); // consume the falling-edge tick

        parser.parseVoltage("0,0,0,4095");
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(inZone),
                "tick 5: new rising edge after baseline → SCORE again");
    }

    @Test
    @DisplayName("Alternating in/out ticks: SCORE on re-entry if voltage still high (wasFiring reset on exit)")
    void alternatingZoneStateBehavior() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 1.0);

        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(true)),  "tick 1: in zone --> SCORE");
        
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(false)), "tick 2: out of zone, high V --> SCORE (late-fire)");

        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(true)),  "tick 3: re-entered zone --> SCORE again");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ── Injector side-effects ─────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("stopInjectionOnly() sends a stop command without closing the shared manager")
    void stopInjectionOnlySendsStopCommand() {
        // Minimal VoltageInjector stub that counts stopInjection calls.
        class TrackingInjector implements VoltageInjector {
            int stopCalls;
            @Override public void injectVoltage(int channel, double volts) {}
            @Override public void stopInjection(int channel) { stopCalls++; }
        }

        TrackingInjector injector = new TrackingInjector();
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), injector);
        p.stopInjectionOnly();
        assertEquals(1, injector.stopCalls, "Closing the HTZ window must stop any active injection exactly once");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ── Two-arg / convenience constructors ────────────────────────────────────
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Two-arg constructor uses NeuralHardwareConfig default threshold")
    void defaultThresholdConstructorScoresAtDefaultThreshold() {
        // DEFAULT_FIRING_THRESHOLD_VOLTS = 0.5 V; voltage 1.0 V ≥ 0.5 V --> SCORE
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(1.0));
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Two-arg constructor: voltage just below default threshold yields null")
    void defaultThresholdJustBelow() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(0.49));
        assertNull(p.getNextMove(new HitTheZoneState(true)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ── Metadata ──────────────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getName() returns the name passed to the constructor")
    void getNameReturnsConstructorValue() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Neural", fixed(2.0), 1.0);
        assertEquals("Neural", p.getName());
    }

    @Test
    @DisplayName("getType() returns PlayerType.HARDWARE")
    void getTypeReturnsHardware() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("bot", fixed(2.0), 1.0);
        assertEquals(PlayerType.HARDWARE, p.getType());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ── Lifecycle ─────────────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("close() does not throw when signal source is a lambda stub")
    void closeDoesNotThrow() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 1.0);
        assertDoesNotThrow(p::close);
    }
}