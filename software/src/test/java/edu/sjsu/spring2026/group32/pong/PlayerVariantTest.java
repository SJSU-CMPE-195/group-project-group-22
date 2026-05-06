package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.model.PlayerType;
import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayerVariant}.
 */
@DisplayName("PlayerVariant Suite")
class PlayerVariantTest {

    // getDisplayName()

    @Test
    @DisplayName("HUMAN display name is 'Human'")
    void humanDisplayName() {
        assertEquals("Human", PlayerVariant.HUMAN.getDisplayName());
    }

    @Test
    @DisplayName("HARDWARE display name is 'Hardware AI'")
    void hardwareDisplayName() {
        assertEquals("Hardware AI", PlayerVariant.HARDWARE.getDisplayName());
    }

    @Test
    @DisplayName("AI_EASY display name is 'Software AI Easy'")
    void aiEasyDisplayName() {
        assertEquals("Software AI Easy", PlayerVariant.AI_EASY.getDisplayName());
    }

    @Test
    @DisplayName("AI_HARD display name is 'Software AI Hard'")
    void aiHardDisplayName() {
        assertEquals("Software AI Hard", PlayerVariant.AI_HARD.getDisplayName());
    }

    // getPlayerType()

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

    // toString()

    @Test
    @DisplayName("toString() returns the display name (used by JComboBox renderer)")
    void toStringReturnsDisplayName() {
        for (PlayerVariant v : PlayerVariant.values()) {
            assertEquals(v.getDisplayName(), v.toString(),
                    v.name() + ".toString() should equal getDisplayName()");
        }
    }

    // completeness

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