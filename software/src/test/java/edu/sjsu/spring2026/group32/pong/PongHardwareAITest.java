package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.HardwareSignalSource;
import edu.sjsu.spring2026.group32.player.PlayerType;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PongHardwareAI}.
 *
 * <p>{@link BaseSignalSource} is a SAM interface, so lambdas are used as
 * lightweight stubs — no mocking framework required.
 *
 * <p>Default threshold = 0.5 V (see {@code PongHardwareAI.DEFAULT_THRESHOLD}).
 */
@DisplayName("PongHardwareAI Suite")
class PongHardwareAITest {

    private static final PongState ANY_STATE = new PongState(100, 200, 300, 600);

    /** Stub source that always returns the given voltage. */
    private static BaseSignalSource fixed(double v) {
        return () -> v;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Action mapping — default threshold (0.5 V)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns LEFT when left source voltage meets default threshold")
    void returnsLeftWhenLeftSourceFires() {
        PongHardwareAI ai = new PongHardwareAI("HW", fixed(3.0), fixed(0.0));
        assertEquals(PongAction.LEFT, ai.getNextMove(ANY_STATE));
    }

    @Test
    @DisplayName("Returns RIGHT when only right source voltage meets default threshold")
    void returnsRightWhenRightSourceFires() {
        PongHardwareAI ai = new PongHardwareAI("HW", fixed(0.0), fixed(3.0));
        assertEquals(PongAction.RIGHT, ai.getNextMove(ANY_STATE));
    }

    @Test
    @DisplayName("Returns IDLE when neither source reaches default threshold")
    void returnsIdleWhenNeitherFires() {
        // NOTE: The default threshold is now NeuralHardwareConfig.DEFAULT_FIRING_THRESHOLD_VOLTS
        // = 0.5V, not 2.0V as the original test assumed.  fixed(0.5) >= 0.5 is true, so
        // LEFT fires and the result is LEFT not IDLE. 
        PongHardwareAI ai = new PongHardwareAI("HW", fixed(0.49), fixed(0.49));
        assertEquals(PongAction.IDLE, ai.getNextMove(ANY_STATE));

    }

    @Test
    @DisplayName("Returns LEFT (not RIGHT) when both sources fire simultaneously — LEFT takes precedence")
    void leftPrecedenceWhenBothSourcesFire() {
        PongHardwareAI ai = new PongHardwareAI("HW", fixed(3.0), fixed(3.0));
        assertEquals(PongAction.LEFT, ai.getNextMove(ANY_STATE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Default threshold boundary (0.5 V)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns LEFT at exactly the default threshold (inclusive)")
    void leftAtExactDefaultThreshold() {
        PongHardwareAI ai = new PongHardwareAI("HW", fixed(0.5), fixed(0.0));
        assertEquals(PongAction.LEFT, ai.getNextMove(ANY_STATE));
    }

    @Test
    @DisplayName("Returns IDLE just below the default threshold")
    void idleJustBelowDefaultThreshold() {
        // NOTE: The default threshold changed from 2.0V to NeuralHardwareConfig
        // .DEFAULT_FIRING_THRESHOLD_VOLTS = 0.5V.
        PongHardwareAI ai = new PongHardwareAI("HW", fixed(0.49), fixed(0.0));
        assertEquals(PongAction.IDLE, ai.getNextMove(ANY_STATE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Custom threshold
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Custom threshold is respected: fires at or above, not below")
    void customThresholdRespected() {
        double threshold = 1.5;
        PongHardwareAI ai = new PongHardwareAI("HW", fixed(1.5), fixed(0.0), threshold);
        assertEquals(PongAction.LEFT, ai.getNextMove(ANY_STATE));

        PongHardwareAI ai2 = new PongHardwareAI("HW", fixed(1.49), fixed(1.49), threshold);
        assertEquals(PongAction.IDLE, ai2.getNextMove(ANY_STATE));
    }

    @Test
    @DisplayName("Four-arg constructor with custom threshold overrides default 0.5 V")
    void fourArgConstructorUsesCustomThreshold() {
        // With threshold=0.1, even 0.5V should trigger LEFT
        PongHardwareAI ai = new PongHardwareAI("HW", fixed(0.5), fixed(0.0), 0.1);
        assertEquals(PongAction.LEFT, ai.getNextMove(ANY_STATE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Metadata
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getName() returns the name passed to the constructor")
    void getNameReturnsConstructorValue() {
        PongHardwareAI ai = new PongHardwareAI("NeuralPong", fixed(0.0), fixed(0.0));
        assertEquals("NeuralPong", ai.getName());
    }

    @Test
    @DisplayName("getType() returns PlayerType.HARDWARE")
    void getTypeReturnsHardware() {
        PongHardwareAI ai = new PongHardwareAI("HW", fixed(0.0), fixed(0.0));
        assertEquals(PlayerType.HARDWARE, ai.getType());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // close() — must not throw when sources are stubs (not HardwareSignalSource)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("close() does not throw when signal sources are lambda stubs")
    void closeDoesNotThrowWithLambdaStubs() {
        PongHardwareAI ai = new PongHardwareAI("HW", fixed(0.0), fixed(0.0));
        assertDoesNotThrow(ai::close);
    }

    @Test
    @DisplayName("close() releases both HardwareSignalSource instances")
    void closeReleasesHardwareSources() {
        HardwareSignalSource mockLeft  = mock(HardwareSignalSource.class);
        HardwareSignalSource mockRight = mock(HardwareSignalSource.class);
        when(mockLeft.getNextVoltage()).thenReturn(0.0);
        when(mockRight.getNextVoltage()).thenReturn(0.0);

        PongHardwareAI ai = new PongHardwareAI("HW", mockLeft, mockRight);
        ai.close();

        verify(mockLeft,  times(1)).close();
        verify(mockRight, times(1)).close();
    }
    
}
