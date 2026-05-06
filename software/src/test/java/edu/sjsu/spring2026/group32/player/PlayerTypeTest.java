package edu.sjsu.spring2026.group32.player;

import edu.sjsu.spring2026.group32.player.model.PlayerType;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PlayerType}.
 */
@DisplayName("PlayerType Suite")
class PlayerTypeTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HUMAN constant exists")
    void humanConstantExists() {
        assertNotNull(PlayerType.HUMAN);
    }

    @Test
    @DisplayName("SOFTWARE constant exists")
    void softwareConstantExists() {
        assertNotNull(PlayerType.SOFTWARE);
    }

    @Test
    @DisplayName("HARDWARE constant exists")
    void hardwareConstantExists() {
        assertNotNull(PlayerType.HARDWARE);
    }

    @Test
    @DisplayName("Exactly three constants are defined")
    void exactlyThreeConstants() {
        assertEquals(3, PlayerType.values().length);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // valueOf
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("valueOf round-trip resolves each constant")
    void valueOfRoundTrip() {
        for (PlayerType t : PlayerType.values()) {
            assertSame(t, PlayerType.valueOf(t.name()));
        }
        
    }

    @Test
    @DisplayName("valueOf with unknown name throws IllegalArgumentException")
    void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> PlayerType.valueOf("ROBOT"));
    }


}
