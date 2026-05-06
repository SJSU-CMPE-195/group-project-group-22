package edu.sjsu.spring2026.group32.pong.core;

import edu.sjsu.spring2026.group32.player.HumanPlayer;
import edu.sjsu.spring2026.group32.player.model.BasePlayer;
import edu.sjsu.spring2026.group32.pong.ai.PongHardwareAI;
import edu.sjsu.spring2026.group32.pong.ai.PongSoftwareAI;
import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;
import edu.sjsu.spring2026.group32.pong.model.PongAction;
import edu.sjsu.spring2026.group32.pong.model.PongState;

import java.awt.event.KeyEvent;
import java.util.Map;

/**
 * Builds Pong players from variant selections.
 */
public final class PongPlayerFactory {
    private PongPlayerFactory() {
    }

    public static BasePlayer<PongState, PongAction> createPlayer(PlayerVariant variant, PongHardwareAI hardwarePlayer) {
        return switch (variant) {
            case HUMAN -> buildHumanPlayer();
            case HARDWARE -> hardwarePlayer != null
                    ? hardwarePlayer
                    : new PongSoftwareAI("Software AI Hard", 8, 1.00);
            case AI_HARD -> new PongSoftwareAI("Software AI Hard", 8, 1.00);
            case AI_EASY -> new PongSoftwareAI("Software AI Easy", 22, 0.72);
        };
    }

    public static HumanPlayer<PongState, PongAction> buildHumanPlayer() {
        return new HumanPlayer<>(
                "Human",
                Map.of(
                        KeyEvent.VK_LEFT, PongAction.LEFT,
                        KeyEvent.VK_RIGHT, PongAction.RIGHT),
                PongAction.IDLE);
    }
}
