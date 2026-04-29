package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.VoltageInjector;
import edu.sjsu.spring2026.group32.player.PlayerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.*;

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
 *       ticks return {@code null} because {@code wasFiring} is set.</li>
 *   <li>Out-of-zone scoring: when the ball is outside the zone and voltage ≥
 *       threshold the source intentionally returns SCORE (late-fire after zone
 *       exit is treated as a valid hit by the game).</li>
 *   <li>Zone transition side-effects: injector is called on entry/exit edges.</li>
 * </ul>
 */
@DisplayName("HitTheZoneHardwareAI Suite")
class HitTheZoneHardwareAITest {

    private static BaseSignalSource fixed(double voltage) {
        return () -> voltage;
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

    // ── Out-of-zone scoring contract ──────────────────────────────────────────

    @Test
    @DisplayName("Returns SCORE when outside zone and voltage meets threshold (late-fire contract)")
    void scoreWhenOutsideZoneAndVoltageHigh() {
        // actionFromVoltage() returns SCORE on the !inZone branch when voltage >= threshold.
        // This is the intended contract: a spike that fires just after the ball exits still counts.
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 1.0);
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(false)));
    }

    @Test
    @DisplayName("Returns null when outside zone and voltage is below threshold")
    void noScoreWhenOutsideZoneAndVoltageLow() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(0.4), 1.0);
        assertNull(p.getNextMove(new HitTheZoneState(false)));
    }

    // ── Rising-edge detection (wasFiring) ────────────────────────────────────

    @Test
    @DisplayName("Scores once per threshold crossing: null on the tick immediately after SCORE while voltage stays high")
    void scoresOncePerThresholdCrossing() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 1.0);
        HitTheZoneState inZone = new HitTheZoneState(true);

        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(inZone), "tick 1: should SCORE on rising edge");
        assertNull(p.getNextMove(inZone), "tick 2: voltage still high — wasFiring suppresses second SCORE");
    }

    @Test
    @DisplayName("Alternating in/out ticks: SCORE on re-entry if voltage still high (wasFiring reset on exit)")
    void alternatingZoneStateBehavior() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 1.0);

        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(true)),  "tick 1: in zone --> SCORE");
        
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(false)), "tick 2: out of zone, high V --> SCORE (late-fire)");

        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(true)),  "tick 3: re-entered zone --> SCORE again");
    }

    // ── Injector side-effects ─────────────────────────────────────────────────

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

    // ── Two-arg / convenience constructors ────────────────────────────────────

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

    // ── Metadata ──────────────────────────────────────────────────────────────

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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("close() does not throw when signal source is a lambda stub")
    void closeDoesNotThrow() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 1.0);
        assertDoesNotThrow(p::close);
    }
}