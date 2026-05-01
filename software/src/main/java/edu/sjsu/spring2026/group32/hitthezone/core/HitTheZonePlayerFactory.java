package edu.sjsu.spring2026.group32.hitthezone.core;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneHardwareAI;
import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneSoftwareAI;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;
import edu.sjsu.spring2026.group32.player.HumanPlayer;
import edu.sjsu.spring2026.group32.player.model.BasePlayer;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds default Hit The Zone player rosters.
 */
public final class HitTheZonePlayerFactory {
    private HitTheZonePlayerFactory() {
    }

    public static List<BasePlayer<HitTheZoneState, HitTheZoneAction>> createDefaultPlayers(
            SerialConnectionManager htzManager) {
        List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players = new ArrayList<>();
        players.add(new HitTheZoneSoftwareAI("Bot Alpha", 3));
        players.add(new HitTheZoneSoftwareAI("Bot Beta", 9));
        players.add(new HumanPlayer<>("Human", Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null));

        if (htzManager != null && htzManager.isConnected()) {
            players.add(HitTheZoneHardwareFactory.createHardwarePlayer(htzManager));
        }

        return players;
    }

    public static List<BasePlayer<HitTheZoneState, HitTheZoneAction>> createStandalonePlayers(
            HitTheZoneHardwareAI hardwarePlayer) {
        return new ArrayList<>(List.of(
                new HitTheZoneSoftwareAI("Bot Alpha", 3),
                new HitTheZoneSoftwareAI("Bot Beta", 9),
                hardwarePlayer,
                new HumanPlayer<>("Human", Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null)
        ));
    }
}
