package edu.sjsu.spring2026.group32.player;

import edu.sjsu.spring2026.group32.player.model.Action;
import edu.sjsu.spring2026.group32.player.model.GameState;
import edu.sjsu.spring2026.group32.player.model.PlayerType;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HumanPlayer Suite")
class HumanPlayerTest {

    // stand-in state + action types
    private record TestState() implements GameState {}
    private enum TestAction implements Action { LEFT, RIGHT, IDLE }

    private static final int LEFT_KEY  = KeyEvent.VK_LEFT;
    private static final int RIGHT_KEY = KeyEvent.VK_RIGHT;
    private static final int OTHER_KEY = KeyEvent.VK_SPACE;

    private HumanPlayer<TestState, TestAction> player;
    private TestState state;

    @BeforeEach
    void setUp() {
        player = new HumanPlayer<>(
            "TestHuman",
            Map.of(LEFT_KEY, TestAction.LEFT, RIGHT_KEY, TestAction.RIGHT),
            TestAction.IDLE);

        state = new TestState();
        
    }

    // initial state

    @Test
    @DisplayName("getNextMove() returns default action before any key event")
    void keyboardInput_updatesPlayerState_defaultAction() {
        assertEquals(TestAction.IDLE, player.getNextMove(state), "player should return default action before any key is pressed");
    }

    // key press

    @Test
    @DisplayName("pressing LEFT key produces LEFT action")
    void keyboardInput_updatesPlayerState_pressLeft() {
        player.keyPressed(keyEvent(LEFT_KEY));

        assertEquals(TestAction.LEFT, player.getNextMove(state), "LEFT key press should produce LEFT action");
    }

    @Test
    @DisplayName("pressing RIGHT key produces RIGHT action")
    void keyboardInput_updatesPlayerState_pressRight() {
        player.keyPressed(keyEvent(RIGHT_KEY));

        assertEquals(TestAction.RIGHT, player.getNextMove(state), "RIGHT key press should produce RIGHT action");
    }

    // Key release

    @Test
    @DisplayName("releasing LEFT key reverts to default action")
    void keyboardInput_updatesPlayerState_releaseLeft() {
        player.keyPressed(keyEvent(LEFT_KEY));
        player.keyReleased(keyEvent(LEFT_KEY));

        assertEquals(TestAction.IDLE, player.getNextMove(state), "releasing LEFT key should revert to default action");
    }

    @Test
    @DisplayName("releasing RIGHT key reverts to default action")
    void keyboardInput_updatesPlayerState_releaseRight() {
        player.keyPressed(keyEvent(RIGHT_KEY));
        player.keyReleased(keyEvent(RIGHT_KEY));

        assertEquals(TestAction.IDLE, player.getNextMove(state), "releasing RIGHT key should revert to default action");
    }

    // Unsupported keys

    @Test
    @DisplayName("pressing an unbound key does not change action")
    void keyboardInput_updatesPlayerState_unboundKeyIgnored() {
        player.keyPressed(keyEvent(OTHER_KEY));

        assertEquals(TestAction.IDLE, player.getNextMove(state), "unbound key press should not change current action");
    }

    @Test
    @DisplayName("releasing an unbound key does not change action")
    void keyboardInput_updatesPlayerState_unboundKeyReleaseIgnored() {
        player.keyPressed(keyEvent(LEFT_KEY));
        player.keyReleased(keyEvent(OTHER_KEY)); // release a different key

        assertEquals(TestAction.LEFT, player.getNextMove(state), "unbound key release should not affect current action");
    }

    // sequence (press, switch, release)

    @Test
    @DisplayName("switching from LEFT to RIGHT without releasing produces RIGHT")
    void keyboardInput_updatesPlayerState_switchDirection() {
        player.keyPressed(keyEvent(LEFT_KEY));
        player.keyPressed(keyEvent(RIGHT_KEY)); // switch without releasing LEFT

        assertEquals(TestAction.RIGHT, player.getNextMove(state), "pressing RIGHT while LEFT is held should switch to RIGHT");
    }

    @Test
    @DisplayName("press LEFT, release LEFT, press RIGHT produces RIGHT then IDLE sequence")
    void keyboardInput_updatesPlayerState_fullSequence() {
        player.keyPressed(keyEvent(LEFT_KEY));
        assertEquals(TestAction.LEFT, player.getNextMove(state), "LEFT after press");

        player.keyReleased(keyEvent(LEFT_KEY));
        assertEquals(TestAction.IDLE, player.getNextMove(state), "IDLE after release");

        player.keyPressed(keyEvent(RIGHT_KEY));
        assertEquals(TestAction.RIGHT, player.getNextMove(state), "RIGHT after second press");

        player.keyReleased(keyEvent(RIGHT_KEY));
        assertEquals(TestAction.IDLE, player.getNextMove(state), "IDLE after second release");
    }

    // metadata

    @Test
    @DisplayName("getName() returns the name supplied to the constructor")
    void keyboardInput_updatesPlayerState_getName() {
        assertEquals("TestHuman", player.getName());
    }

    @Test
    @DisplayName("getType() returns PlayerType.HUMAN")
    void keyboardInput_updatesPlayerState_getType() {
        assertEquals(PlayerType.HUMAN, player.getType());
    }

    // helper

    private static KeyEvent keyEvent(int keyCode) {
        return new KeyEvent(new Component() {}, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }
}