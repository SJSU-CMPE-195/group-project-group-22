package edu.sjsu.spring2026.group32.pong.core;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.pong.ai.PongHardwareAI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PongHardwareFactory Suite")
class PongHardwareFactoryTest {

    private SerialConnectionManager mockManager;

    @BeforeEach
    void setUp() {
        mockManager = mock(SerialConnectionManager.class);
    }

    @Test
    @DisplayName("createHardwarePlayer: builds expected player when manager is fully ready")
    void createHardwarePlayer_buildsExpectedPlayer() {
        when(mockManager.isConnected()).thenReturn(true);
        when(mockManager.getDeviceChannelCount()).thenReturn(2);

        PongHardwareAI player = PongHardwareFactory.createHardwarePlayer(mockManager);

        assertNotNull(player, "Factory should return a valid PongHardwareAI instance");

        assertEquals("Hardware", player.getName());
    }

    @Test
    @DisplayName("createHardwarePlayer: returns null if manager is null")
    void createHardwarePlayer_nullManager_returnsNull() {
        assertNull(PongHardwareFactory.createHardwarePlayer(null));
    }

    @Test
    @DisplayName("createHardwarePlayer: returns null if manager is disconnected")
    void createHardwarePlayer_disconnected_returnsNull() {
        when(mockManager.isConnected()).thenReturn(false);
        when(mockManager.getDeviceChannelCount()).thenReturn(2);

        assertNull(PongHardwareFactory.createHardwarePlayer(mockManager));
    }

    @Test
    @DisplayName("createHardwarePlayer: returns null if channel count is insufficient")
    void createHardwarePlayer_lowChannelCount_returnsNull() {
        when(mockManager.isConnected()).thenReturn(true);
        
        when(mockManager.getDeviceChannelCount()).thenReturn(1);

        assertNull(PongHardwareFactory.createHardwarePlayer(mockManager), "Should return null because Pong requires at least 2 channels");
    }
}