package edu.sjsu.spring2026.group32.player;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.HardwareSignalSource;
import edu.sjsu.spring2026.group32.sandbox.HitTheZoneAction;
import edu.sjsu.spring2026.group32.sandbox.HitTheZoneState;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HardwareAIPlayer}.
 *
 * <p>{@code HardwareAIPlayer} is abstract; a minimal concrete subclass
 * ({@link ThresholdAI}) is defined here for testing.  It scores when
 * voltage ≥ 1.0 V and the ball is in zone, otherwise returns {@code null}.
 */
@DisplayName("HardwareAIPlayer Suite")
class HardwareAIPlayerTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Concrete test subclass
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Minimal concrete subclass of HardwareAIPlayer for testing purposes.
     *
     * <p>Scores when {@code voltage >= 1.0} and {@code state.inZone()}.
     */
    static class ThresholdAI extends HardwareAIPlayer<HitTheZoneState, HitTheZoneAction> {

        ThresholdAI(String name, BaseSignalSource source) {
            super(name, source);
        }

        @Override
        protected HitTheZoneAction voltageToAction(HitTheZoneState state, double voltage) {
            if (state.inZone() && voltage >= 1.0) return HitTheZoneAction.SCORE;
            return null;
        }
    }

    private static BaseSignalSource fixed(double v) {
        return () -> v;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getNextMove(), delegates to voltageToAction()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getNextMove() calls voltageToAction() with the signal source voltage")
    void getNextMoveDelegatesToVoltageToAction_scores() {
        ThresholdAI ai = new ThresholdAI("bot", fixed(2.0));
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("getNextMove() returns null when voltageToAction() returns null")
    void getNextMoveDelegatesToVoltageToAction_null() {
        ThresholdAI ai = new ThresholdAI("bot", fixed(0.5)); // below 1.0 V
        assertNull(ai.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("getNextMove() passes current game state to voltageToAction()")
    void getNextMovePassesStateCorrectly() {
        ThresholdAI ai = new ThresholdAI("bot", fixed(3.0));
        // if in zone it should score
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(new HitTheZoneState(true)));
        // if out of zone it should be null
        assertNull(ai.getNextMove(new HitTheZoneState(false)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Metadata
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getName() returns the name passed to the constructor")
    void getNameReturnsConstructorValue() {
        ThresholdAI ai = new ThresholdAI("NeuralBot", fixed(0.0));
        assertEquals("NeuralBot", ai.getName());
    }

    @Test
    @DisplayName("getType() returns PlayerType.HARDWARE")
    void getTypeReturnsHardware() {
        ThresholdAI ai = new ThresholdAI("bot", fixed(0.0));
        assertEquals(PlayerType.HARDWARE, ai.getType());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // close()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("close() does not throw when signal source is a lambda stub")
    void closeDoesNotThrowWithLambdaStub() {
        ThresholdAI ai = new ThresholdAI("bot", fixed(0.0));
        assertDoesNotThrow(ai::close);
    }

    @Test
    @DisplayName("close() can be called multiple times without error")
    void closeIsIdempotent() {
        ThresholdAI ai = new ThresholdAI("bot", fixed(0.0));
        assertDoesNotThrow(() -> {
            ai.close();
            ai.close(); // second call should also be safe

        });
    }

    @Test
    @DisplayName("close() calls close() on HardwareSignalSource when present")
    void closeCallsHardwareSignalSourceClose() {
        HardwareSignalSource mockSource = mock(HardwareSignalSource.class);
        when(mockSource.getNextVoltage()).thenReturn(0.0);

        ThresholdAI ai = new ThresholdAI("bot", mockSource);
        ai.close();

        verify(mockSource, times(1)).close();
    }

}
