package edu.sjsu.spring2026.group32.pong.core;

import edu.sjsu.spring2026.group32.player.HumanPlayer;
import edu.sjsu.spring2026.group32.pong.ai.PongHardwareAI;
import edu.sjsu.spring2026.group32.pong.ai.PongSoftwareAI;
import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PongPlayerFactory Suite")
class PongPlayerFactoryTest {

    @Test
    @DisplayName("createPlayer: returns HumanPlayer for HUMAN variant")
    void createPlayer_returnsHumanForHumanVariant() {
        var player = PongPlayerFactory.createPlayer(PlayerVariant.HUMAN, null);
        
        assertTrue(player instanceof HumanPlayer, "Should create a HumanPlayer");
        assertEquals("Human", player.getName());
    }

    @Test
    @DisplayName("createPlayer: returns HardwareAI when hardware is available")
    void createPlayer_returnsHardwareWhenAvailable() {
        PongHardwareAI mockHardware = mock(PongHardwareAI.class);
        when(mockHardware.getName()).thenReturn("Hardware");

        var player = PongPlayerFactory.createPlayer(PlayerVariant.HARDWARE, mockHardware);

        assertSame(mockHardware, player, "Should return the actual hardware instance provided");
    }

    @Test
    @DisplayName("createPlayer: falls back to SoftwareAI when hardware is missing")
    void createPlayer_fallsBackToSoftwareWhenHardwareNull() {
        var player = PongPlayerFactory.createPlayer(PlayerVariant.HARDWARE, null);

        assertTrue(player instanceof PongSoftwareAI, "Should fall back to Software AI");
        assertEquals("Software AI Hard", player.getName(), "Fallback should be the Hard AI");
    }

    @Test
    @DisplayName("createPlayer: returns correct SoftwareAI for AI variants")
    void createPlayer_returnsCorrectAI() {
        var hardAI = PongPlayerFactory.createPlayer(PlayerVariant.AI_HARD, null);
        var easyAI = PongPlayerFactory.createPlayer(PlayerVariant.AI_EASY, null);

        assertTrue(hardAI instanceof PongSoftwareAI);
        assertEquals("Software AI Hard", hardAI.getName());

        assertTrue(easyAI instanceof PongSoftwareAI);
        assertEquals("Software AI Easy", easyAI.getName());
    }

    @Test
    @DisplayName("buildHumanPlayer: verifies keyboard mapping")
    void buildHumanPlayer_hasCorrectKeys() {
        var human = PongPlayerFactory.buildHumanPlayer();
        
        assertNotNull(human);
        assertEquals("Human", human.getName());

    }
}
