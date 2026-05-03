package edu.sjsu.spring2026.group32.hitthezone.core;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneHardwareAI;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HitTheZoneHardwareFactory Suite")
class HitTheZoneHardwareFactoryTest {

    @Test
    @DisplayName("createHardwarePlayer: builds expected player with valid manager")
    void createHardwarePlayer_successPath() {
        SerialConnectionManager mockManager = mock(SerialConnectionManager.class);
        
        HitTheZoneHardwareAI player = HitTheZoneHardwareFactory.createHardwarePlayer(mockManager);

        assertNotNull(player, "Factory should return a valid player instance");
        assertEquals("Neural", player.getName());
        
        verify(mockManager, atLeastOnce()).addListener(any());
    }

    @Test
    @DisplayName("createHardwarePlayer: handles null manager by throwing or failing gracefully")
    void createHardwarePlayer_nullManager_throwsException() {
        assertThrows(NullPointerException.class, () -> {
            HitTheZoneHardwareFactory.createHardwarePlayer(null);
        }, "The AI constructor requires a manager and should throw NPE if given null");
        
    }
}