package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.model.PlayerType;
import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PlayerVariant}.
 */
@DisplayName("PlayerVariant Suite")
class PlayerVariantTest {

    // ──────────────────────────────────────────────────────────────────────────
    // getDisplayName()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HUMAN display name is 'Human'")
    void humanDisplayName() {
        assertEquals("Human", PlayerVariant.HUMAN.getDisplayName());
    }

    @Test
    @DisplayName("HARDWARE display name is 'Hardware'")
    void hardwareDisplayName() {
        assertEquals("Hardware", PlayerVariant.HARDWARE.getDisplayName());
    }

    @Test
    @DisplayName("AI_EASY display name is 'AI Easy'")
    void aiEasyDisplayName() {
        assertEquals("AI Easy", PlayerVariant.AI_EASY.getDisplayName());
    }

    @Test
    @DisplayName("AI_HARD display name is 'AI Hard'")
    void aiHardDisplayName() {
        assertEquals("AI Hard", PlayerVariant.AI_HARD.getDisplayName());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getPlayerType()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HUMAN variant maps to PlayerType.HUMAN")
    void humanVariantType() {
        assertEquals(PlayerType.HUMAN, PlayerVariant.HUMAN.getPlayerType());
    }

    @Test
    @DisplayName("HARDWARE variant maps to PlayerType.HARDWARE")
    void hardwareVariantType() {
        assertEquals(PlayerType.HARDWARE, PlayerVariant.HARDWARE.getPlayerType());
    }

    @Test
    @DisplayName("AI_EASY variant maps to PlayerType.SOFTWARE")
    void aiEasyVariantType() {
        assertEquals(PlayerType.SOFTWARE, PlayerVariant.AI_EASY.getPlayerType());
    }

    @Test
    @DisplayName("AI_HARD variant maps to PlayerType.SOFTWARE")
    void aiHardVariantType() {
        assertEquals(PlayerType.SOFTWARE, PlayerVariant.AI_HARD.getPlayerType());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // toString()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString() returns the display name (used by JComboBox renderer)")
    void toStringReturnsDisplayName() {
        for (PlayerVariant v : PlayerVariant.values()) {
            assertEquals(v.getDisplayName(), v.toString(), v.name() + ".toString() should equal getDisplayName()");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Completeness
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Exactly four variants are defined")
    void exactlyFourVariants() {
        assertEquals(4, PlayerVariant.values().length);
    }

    @Test
    @DisplayName("valueOf resolves each constant by name")
    void valueOfRoundTrip() {
        for (PlayerVariant v : PlayerVariant.values()) {
            assertSame(v, PlayerVariant.valueOf(v.name()));
        }
    }

    @Test
    @DisplayName("valueOf with unknown name throws IllegalArgumentException")
    void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> PlayerVariant.valueOf("ROBOT"));
    }
}
