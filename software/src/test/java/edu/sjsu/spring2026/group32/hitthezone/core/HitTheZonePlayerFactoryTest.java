package edu.sjsu.spring2026.group32.hitthezone.core;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneHardwareAI;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;
import edu.sjsu.spring2026.group32.player.HumanPlayer;
import edu.sjsu.spring2026.group32.player.model.BasePlayer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HitTheZonePlayerFactory Suite")
class HitTheZonePlayerFactoryTest {

    @Test
    @DisplayName("createDefaultPlayers: verifies roster size based on hardware connection")
    void defaultPlayerCreation_coversPositiveAndNegativeCases() {
        List<BasePlayer<HitTheZoneState, HitTheZoneAction>> offlineRoster = HitTheZonePlayerFactory.createDefaultPlayers(null);
        
        assertEquals(3, offlineRoster.size(), "Offline roster should contain 3 players");
        assertEquals("Bot Alpha", offlineRoster.get(0).getName());
        assertTrue(offlineRoster.get(2) instanceof HumanPlayer);

        // case: manager is connected
        SerialConnectionManager mockManager = mock(SerialConnectionManager.class);
        when(mockManager.isConnected()).thenReturn(true);
        
        List<BasePlayer<HitTheZoneState, HitTheZoneAction>> onlineRoster = HitTheZonePlayerFactory.createDefaultPlayers(mockManager);
        
        assertEquals(4, onlineRoster.size(), "Online roster should include hardware player");
        assertEquals("Neural", onlineRoster.get(3).getName());

    }

    @Test
    @DisplayName("createStandalonePlayers: builds expected roster with provided hardware")
    void standalonePlayerCreation_buildsExpectedRoster() {
        // case: valid hardware AI provided
        HitTheZoneHardwareAI mockHardware = mock(HitTheZoneHardwareAI.class);
        when(mockHardware.getName()).thenReturn("Neural");

        List<BasePlayer<HitTheZoneState, HitTheZoneAction>> roster = HitTheZonePlayerFactory.createStandalonePlayers(mockHardware);

        assertEquals(4, roster.size());
        assertSame(mockHardware, roster.get(2));

        // case: null hardware AI provided
        assertThrows(NullPointerException.class, () -> {
            HitTheZonePlayerFactory.createStandalonePlayers(null);
        }, "List.of() will throw NPE if hardwarePlayer is null");

    }
}