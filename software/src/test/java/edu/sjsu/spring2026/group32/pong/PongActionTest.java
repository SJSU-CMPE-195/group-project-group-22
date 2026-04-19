package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.Action;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PongAction}.
 */
@DisplayName("PongAction Suite")
class PongActionTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Enum constants
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LEFT constant exists")
    void leftConstantExists() {
        assertNotNull(PongAction.LEFT);
    }

    @Test
    @DisplayName("RIGHT constant exists")
    void rightConstantExists() {
        assertNotNull(PongAction.RIGHT);
    }

    @Test
    @DisplayName("IDLE constant exists")
    void idleConstantExists() {
        assertNotNull(PongAction.IDLE);
    }

    @Test
    @DisplayName("Exactly three constants are defined")
    void exactlyThreeConstants() {
        assertEquals(3, PongAction.values().length);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // valueOf / name round-trip
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("valueOf('LEFT') resolves to LEFT")
    void valueOfLeft() {
        assertSame(PongAction.LEFT, PongAction.valueOf("LEFT"));
    }

    @Test
    @DisplayName("valueOf('RIGHT') resolves to RIGHT")
    void valueOfRight() {
        assertSame(PongAction.RIGHT, PongAction.valueOf("RIGHT"));
    }

    @Test
    @DisplayName("valueOf('IDLE') resolves to IDLE")
    void valueOfIdle() {
        assertSame(PongAction.IDLE, PongAction.valueOf("IDLE"));
    }

    @Test
    @DisplayName("valueOf with unknown name throws IllegalArgumentException")
    void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> PongAction.valueOf("UP"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Interface contract
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Every PongAction implements the Action marker interface")
    void allConstantsImplementAction() {
        for (PongAction action : PongAction.values()) {
            assertInstanceOf(Action.class, action, action.name() + " should implement Action");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Ordinal ordering
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ordinals match declaration order: LEFT=0, RIGHT=1, IDLE=2")
    void ordinalsMatchDeclarationOrder() {
        assertEquals(0, PongAction.LEFT.ordinal());
        assertEquals(1, PongAction.RIGHT.ordinal());
        assertEquals(2, PongAction.IDLE.ordinal());
    }
}
