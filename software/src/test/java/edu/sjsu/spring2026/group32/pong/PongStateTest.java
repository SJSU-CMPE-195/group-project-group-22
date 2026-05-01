package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.model.GameState;
import edu.sjsu.spring2026.group32.pong.model.PongState;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PongState}.
 */
@DisplayName("PongState Suite")
class PongStateTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Accessors
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("paddleX() returns the value supplied to the constructor")
    void paddleXAccessor() {
        PongState state = new PongState(50, 200, 300, 600);
        assertEquals(50, state.paddleX());
    }

    @Test
    @DisplayName("ballX() returns the value supplied to the constructor")
    void ballXAccessor() {
        PongState state = new PongState(50, 200, 300, 600);
        assertEquals(200, state.ballX());
    }

    @Test
    @DisplayName("ballY() returns the value supplied to the constructor")
    void ballYAccessor() {
        PongState state = new PongState(50, 200, 300, 600);
        assertEquals(300, state.ballY());
    }

    @Test
    @DisplayName("fieldWidth() returns the value supplied to the constructor")
    void fieldWidthAccessor() {
        PongState state = new PongState(50, 200, 300, 600);
        assertEquals(600, state.fieldWidth());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Record equality & hash
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Two PongState records with same values are equal")
    void equalRecordsAreEqual() {
        assertEquals(new PongState(10, 20, 30, 400), new PongState(10, 20, 30, 400));
    }

    @Test
    @DisplayName("PongState records with any differing field are not equal")
    void differentRecordsAreNotEqual() {
        PongState base = new PongState(10, 20, 30, 400);
        assertNotEquals(base, new PongState(99, 20, 30, 400)); // different paddleX
        assertNotEquals(base, new PongState(10, 99, 30, 400)); // different ballX
        assertNotEquals(base, new PongState(10, 20, 99, 400)); // different ballY
        assertNotEquals(base, new PongState(10, 20, 30, 999)); // different fieldWidth
    }

    @Test
    @DisplayName("Equal records share the same hashCode")
    void equalRecordsShareHashCode() {
        assertEquals(
            new PongState(10, 20, 30, 400).hashCode(),
            new PongState(10, 20, 30, 400).hashCode()
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Interface contract
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PongState implements GameState")
    void implementsGameState() {
        assertInstanceOf(GameState.class, new PongState(0, 0, 0, 0));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Boundary / edge values
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PongState accepts zero values without error")
    void acceptsZeroValues() {
        PongState state = new PongState(0, 0, 0, 0);
        assertEquals(0, state.paddleX());
        assertEquals(0, state.ballX());
        assertEquals(0, state.ballY());
        assertEquals(0, state.fieldWidth());
    }

    @Test
    @DisplayName("PongState accepts negative values without error")
    void acceptsNegativeValues() {
        PongState state = new PongState(-1, -100, -200, -600);
        assertEquals(-1,   state.paddleX());
        assertEquals(-100, state.ballX());
        assertEquals(-200, state.ballY());
        assertEquals(-600, state.fieldWidth());
    }
}
