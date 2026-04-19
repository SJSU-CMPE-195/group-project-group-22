package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.player.Action;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HitTheZoneAction}.
 *
 * <p>Verifies all enum constants exist, implement the {@link Action} marker
 * interface, and resolve correctly by name.
 */
@DisplayName("HitTheZoneAction Suite")
class HitTheZoneActionTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Enum constants
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SCORE constant exists")
    void scoreConstantExists() {
        assertNotNull(HitTheZoneAction.SCORE);
    }

    @Test
    @DisplayName("PAUSE constant exists")
    void pauseConstantExists() {
        assertNotNull(HitTheZoneAction.PAUSE);
    }

    @Test
    @DisplayName("RESET constant exists")
    void resetConstantExists() {
        assertNotNull(HitTheZoneAction.RESET);
    }

    @Test
    @DisplayName("Exactly three constants are defined")
    void exactlyThreeConstants() {
        assertEquals(3, HitTheZoneAction.values().length);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // valueOf / name round-trip
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("valueOf('SCORE') resolves to the SCORE constant")
    void valueOfScore() {
        assertSame(HitTheZoneAction.SCORE, HitTheZoneAction.valueOf("SCORE"));
    }

    @Test
    @DisplayName("valueOf('PAUSE') resolves to the PAUSE constant")
    void valueOfPause() {
        assertSame(HitTheZoneAction.PAUSE, HitTheZoneAction.valueOf("PAUSE"));
    }

    @Test
    @DisplayName("valueOf('RESET') resolves to the RESET constant")
    void valueOfReset() {
        assertSame(HitTheZoneAction.RESET, HitTheZoneAction.valueOf("RESET"));
    }

    @Test
    @DisplayName("valueOf with unknown name throws IllegalArgumentException")
    void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class,  () -> HitTheZoneAction.valueOf("UNKNOWN"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Interface contract
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HitTheZoneAction implements Action marker interface")
    void implementsActionInterface() {
        for (HitTheZoneAction action : HitTheZoneAction.values()) {
            assertInstanceOf(Action.class, action, action.name() + " should implement Action");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Ordinal ordering (documents intended order; update if enum is reordered)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Enum ordinals match declaration order: SCORE=0, PAUSE=1, RESET=2")
    void ordinalsMatchDeclarationOrder() {
        assertEquals(0, HitTheZoneAction.SCORE.ordinal());
        assertEquals(1, HitTheZoneAction.PAUSE.ordinal());
        assertEquals(2, HitTheZoneAction.RESET.ordinal());
    }
}
