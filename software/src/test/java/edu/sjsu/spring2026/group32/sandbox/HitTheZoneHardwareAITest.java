package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.VoltageInjector;
import edu.sjsu.spring2026.group32.player.PlayerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HitTheZoneHardwareAI}.
 *
 * <p>{@link BaseSignalSource} is a SAM interface, so lambdas are used as
 * test stubs - no mocking framework required.
 */
class HitTheZoneHardwareAITest {

    private HitTheZoneHardwareAI player;

    private static BaseSignalSource fixed(double voltage) {
        return () -> voltage;
    }

    @BeforeEach
    void setUp() {
        player = new HitTheZoneHardwareAI("bot", fixed(2.0), 1.0);
    }

    @Test
    @DisplayName("Returns SCORE when in zone and voltage meets threshold")
    void scoreWhenInZoneAndVoltageHigh() {
        assertEquals(HitTheZoneAction.SCORE, player.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Returns null when in zone but voltage is below threshold")
    void noScoreWhenVoltageLow() {
        HitTheZoneHardwareAI lowPlayer = new HitTheZoneHardwareAI("Bot", fixed(0.5), 1.0);
        assertNull(lowPlayer.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Returns null when outside zone regardless of voltage")
    void noScoreWhenOutsideZone() {
        HitTheZoneHardwareAI hotPlayer = new HitTheZoneHardwareAI("Bot", fixed(3.3), 1.0);
        assertNull(hotPlayer.getNextMove(new HitTheZoneState(false)));
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
    @DisplayName("Custom threshold is respected")
    void customThresholdRespected() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 2.5);
        assertNull(p.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("getName() returns the name passed to the constructor")
    void getNameReturnsConstructorValue() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Neural", fixed(2.0), 1.0);
        assertEquals("Neural", p.getName());
    }

    @Test
    @DisplayName("getType() returns HARDWARE")
    void getTypeReturnsHardware() {
        assertEquals(PlayerType.HARDWARE, player.getType());
    }

    @Test
    @DisplayName("Two-arg convenience constructor uses default threshold")
    void defaultThresholdConstructorUsesSharedDefault() {
        HitTheZoneHardwareAI defaultPlayer = new HitTheZoneHardwareAI("Bot", fixed(1.0));
        assertEquals(HitTheZoneAction.SCORE, defaultPlayer.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Two-arg convenience constructor: voltage just below threshold does not score")
    void defaultThresholdJustBelow() {
        HitTheZoneHardwareAI defaultPlayer = new HitTheZoneHardwareAI("Bot", fixed(0.49));
        assertNull(defaultPlayer.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("close() does not throw when signal source is a lambda stub")
    void closeDoesNotThrow() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 1.0);
        assertDoesNotThrow(p::close);
    }

    @Test
    @DisplayName("stopInjectionOnly() sends a stop command without needing to close the shared manager")
    void stopInjectionOnlySendsStopCommand() {
        class TrackingInjector implements VoltageInjector {
            private int stopCalls;

            @Override
            public void injectVoltage(int channel, double volts) {}

            @Override
            public void stopInjection(int channel) {
                stopCalls++;
                assertEquals(0, channel, "stopInjectionOnly() should stop all channels");
            }
        }

        TrackingInjector injector = new TrackingInjector();
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), injector);

        p.stopInjectionOnly();

        assertEquals(1, injector.stopCalls,
                "Closing the Hit The Zone window must stop any active injection");
    }

    @Test
    @DisplayName("Scores once per threshold crossing while voltage stays high")
    void scoresOncePerThresholdCrossing() {
        assertEquals(HitTheZoneAction.SCORE, player.getNextMove(new HitTheZoneState(true)));
        assertNull(player.getNextMove(new HitTheZoneState(true)));
        assertNull(player.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Alternating in/out-of-zone ticks produce SCORE only when in zone")
    void alternatingZoneStateBehavior() {
        assertEquals(HitTheZoneAction.SCORE, player.getNextMove(new HitTheZoneState(true)));
        assertNull(player.getNextMove(new HitTheZoneState(false)));
        assertEquals(HitTheZoneAction.SCORE, player.getNextMove(new HitTheZoneState(true)));
        assertNull(player.getNextMove(new HitTheZoneState(false)));
    }
}
