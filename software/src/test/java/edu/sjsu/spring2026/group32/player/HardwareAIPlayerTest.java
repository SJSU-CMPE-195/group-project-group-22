package edu.sjsu.spring2026.group32.player;

import edu.sjsu.spring2026.group32.player.model.Action;
import edu.sjsu.spring2026.group32.player.model.GameState;
import edu.sjsu.spring2026.group32.player.model.PlayerType;
import edu.sjsu.spring2026.group32.hardware.signal.BaseSignalSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;

@DisplayName("HardwareAIPlayer Suite")
class HardwareAIPlayerTest {

    private static final double THRESHOLD = 1.0;
    private static final double ABOVE = THRESHOLD + 0.1;
    private static final double BELOW = THRESHOLD - 0.1;

    // Action enum for tests
    private enum TestAction implements Action { SCORE }

    // GameState stand in for tests
    private record TestState() implements GameState {}

    // raw-voltage subclass, delegates to voltage parameter directly
    private static class RawVoltagePlayer
            extends HardwareAIPlayer<TestState, TestAction> {
        final double threshold;

        RawVoltagePlayer(String name, BaseSignalSource source, double threshold) {
            super(name, source);
            this.threshold = threshold;
        }

        @Override
        protected TestAction voltageToAction(TestState state, double voltage) {
            return voltage >= threshold ? TestAction.SCORE : null;
        }

    }

    // spikeike-aware subclass delegates to sourceHasSpike
    private static class SpikePlayer
            extends HardwareAIPlayer<TestState, TestAction> {
        final double threshold;

        SpikePlayer(String name, BaseSignalSource source, double threshold) {
            super(name, source);
            this.threshold = threshold;
        }

        @Override
        protected TestAction voltageToAction(TestState state, double voltage) {
            return sourceHasSpike(threshold) ? TestAction.SCORE : null;
        }
    }

    // Stub signal source

    private static class StubSignalSource implements BaseSignalSource {
        private double voltage = 0.0;
        void setVoltage(double v) { this.voltage = v; }
        @Override public double getNextVoltage() { return voltage; }
    }

    // Setup

    private StubSignalSource source;
    private RawVoltagePlayer rawPlayer;
    private SpikePlayer spikePlayer;
    private TestState state;

    @BeforeEach
    void setUp() {
        source = new StubSignalSource();
        rawPlayer = new RawVoltagePlayer("RawTest", source, THRESHOLD);
        spikePlayer = new SpikePlayer("SpikeTest", source, THRESHOLD);
        state = new TestState();
    }

    // getNextMove, raw voltage path

    @Test
    @DisplayName("getNextMove() returns action when voltage is at or above threshold")
    void getNextMove_handlesPositiveAndNegativeSignalCases_aboveThreshold() {
        source.setVoltage(ABOVE);
        assertEquals(TestAction.SCORE, rawPlayer.getNextMove(state), "voltage above threshold should produce SCORE");
    }

    @Test
    @DisplayName("getNextMove() returns null when voltage is below threshold")
    void getNextMove_handlesPositiveAndNegativeSignalCases_belowThreshold() {
        source.setVoltage(BELOW);
        assertNull(rawPlayer.getNextMove(state), "voltage below threshold should return null");
    }

    @Test
    @DisplayName("getNextMove() returns action when voltage is exactly at threshold (>=)")
    void getNextMove_handlesPositiveAndNegativeSignalCases_exactlyAtThreshold() {
        source.setVoltage(THRESHOLD);
        assertEquals(TestAction.SCORE, rawPlayer.getNextMove(state), "voltage exactly at threshold should produce SCORE (>=)");
    }

    @Test
    @DisplayName("getNextMove() returns null at zero voltage")
    void getNextMove_handlesPositiveAndNegativeSignalCases_zeroVoltage() {
        source.setVoltage(0.0);
        assertNull(rawPlayer.getNextMove(state), "zero voltage should return null");
    }

    // getNextMove, sourceHasSpike path (stateless default from BaseSignalSource)

    @Test
    @DisplayName("sourceHasSpike() fires when voltage is above threshold")
    void getNextMove_handlesPositiveAndNegativeSignalCases_spikeAbove() {
        source.setVoltage(ABOVE);
        assertEquals(TestAction.SCORE, spikePlayer.getNextMove(state), "sourceHasSpike() should fire above threshold");

    }

    @Test
    @DisplayName("sourceHasSpike() does not fire when voltage is below threshold")
    void getNextMove_handlesPositiveAndNegativeSignalCases_spikeBelow() {
        source.setVoltage(BELOW);
        assertNull(spikePlayer.getNextMove(state), "sourceHasSpike() should not fire below threshold");
    }

    // metadata

    @Test
    @DisplayName("getName() returns the name supplied to the constructor")
    void getNextMove_handlesPositiveAndNegativeSignalCases_getName() {
        assertEquals("RawTest", rawPlayer.getName());
        assertEquals("SpikeTest", spikePlayer.getName());
    }

    @Test
    @DisplayName("getType() always returns PlayerType.HARDWARE")
    void getNextMove_handlesPositiveAndNegativeSignalCases_getType() {
        assertEquals(PlayerType.HARDWARE, rawPlayer.getType());
        assertEquals(PlayerType.HARDWARE, spikePlayer.getType());
    }

    // close() and repeated calls

    @Test
    @DisplayName("close() on a stub-backed player completes without throwing")
    void lifecycleMethods_releaseResourcesSafely_closeWithStub() {
        rawPlayer.close();
        spikePlayer.close();
    }

    @Test
    @DisplayName("close() can be called multiple times without throwing")
    void lifecycleMethods_releaseResourcesSafely_repeatedClose() {
        rawPlayer.close();
        rawPlayer.close();
    }

    @Test
    @DisplayName("getNextMove() still works after close() with a stub source")
    void lifecycleMethods_releaseResourcesSafely_getNextMoveAfterClose() {
        rawPlayer.close();
        source.setVoltage(ABOVE);
        assertEquals(TestAction.SCORE, rawPlayer.getNextMove(state), "player should still function after close() with a stub source");
    }
}