package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.player.GameState;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HitTheZoneState}.
 *
 * <p>Verifies the record's accessor, equality, and interface contract.
 */
@DisplayName("HitTheZoneState Suite")
class HitTheZoneStateTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Construction & accessor
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("inZone() returns true when constructed with true")
    void inZoneTrueWhenConstructedTrue() {
        HitTheZoneState state = new HitTheZoneState(true);
        assertTrue(state.inZone());
    }

    @Test
    @DisplayName("inZone() returns false when constructed with false")
    void inZoneFalseWhenConstructedFalse() {
        HitTheZoneState state = new HitTheZoneState(false);
        assertFalse(state.inZone());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Record equality & hash
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Two HitTheZoneState records with same value are equal")
    void equalRecordsAreEqual() {
        assertEquals(new HitTheZoneState(true),  new HitTheZoneState(true));
        assertEquals(new HitTheZoneState(false), new HitTheZoneState(false));
    }

    @Test
    @DisplayName("Two HitTheZoneState records with different values are not equal")
    void differentRecordsAreNotEqual() {
        assertNotEquals(new HitTheZoneState(true), new HitTheZoneState(false));
    }

    @Test
    @DisplayName("Equal records share the same hashCode")
    void equalRecordsShareHashCode() {
        assertEquals(
            new HitTheZoneState(true).hashCode(),
            new HitTheZoneState(true).hashCode()
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Interface contract
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HitTheZoneState implements GameState")
    void implementsGameState() {
        assertInstanceOf(GameState.class, new HitTheZoneState(true));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // toString (record auto-generates it)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString() includes the inZone value")
    void toStringContainsInZone() {
        assertTrue(new HitTheZoneState(true).toString().contains("true"));
        assertTrue(new HitTheZoneState(false).toString().contains("false"));
    }
}
