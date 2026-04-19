package edu.sjsu.spring2026.group32.player;

import edu.sjsu.spring2026.group32.pong.PongAction;
import edu.sjsu.spring2026.group32.pong.PongState;
import org.junit.jupiter.api.*;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link HumanPlayer}.
 *
 * <p>Uses Mockito to provide a {@link Component} stub as the KeyEvent source —
 * no AWT display is required.
 */
@DisplayName("HumanPlayer Suite")
class HumanPlayerTest {

    // Dummy game state — HumanPlayer ignores it. 
    private static final PongState DUMMY_STATE = new PongState(0, 0, 0, 600);

    // Key bindings used across most tests.
    private static final Map<Integer, PongAction> BINDINGS = Map.of(
        KeyEvent.VK_LEFT,  PongAction.LEFT,
        KeyEvent.VK_RIGHT, PongAction.RIGHT
    );

    /** Non-null component required by KeyEvent constructor. */
    private final Component src = mock(Component.class);

    private HumanPlayer<PongState, PongAction> player;

    @BeforeEach
    void setUp() {
        player = new HumanPlayer<>("P1", BINDINGS, PongAction.IDLE);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Default state
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getNextMove() returns the default action before any key event")
    void defaultActionBeforeKeyEvent() {
        assertEquals(PongAction.IDLE, player.getNextMove(DUMMY_STATE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // keyPressed — sets action
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("keyPressed with bound key changes current action")
    void keyPressedBoundKeyChangesAction() {
        player.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT));
        assertEquals(PongAction.LEFT, player.getNextMove(DUMMY_STATE));
    }

    @Test
    @DisplayName("keyPressed with second bound key changes action to new key's action")
    void keyPressedSecondBoundKey() {
        player.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT));
        player.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_RIGHT));
        assertEquals(PongAction.RIGHT, player.getNextMove(DUMMY_STATE));
    }

    @Test
    @DisplayName("keyPressed with unbound key does NOT change current action")
    void keyPressedUnboundKeyIgnored() {
        player.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT));
        player.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_UP)); // not bound
        assertEquals(PongAction.LEFT, player.getNextMove(DUMMY_STATE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // keyReleased — resets to default
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("keyReleased with bound key resets action to default")
    void keyReleasedResetsToDefault() {
        player.keyPressed(keyEvent(KeyEvent.KEY_PRESSED,  KeyEvent.VK_LEFT));
        player.keyReleased(keyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_LEFT));
        assertEquals(PongAction.IDLE, player.getNextMove(DUMMY_STATE));
    }

    @Test
    @DisplayName("keyReleased with unbound key does NOT reset the current action")
    void keyReleasedUnboundKeyIgnored() {
        player.keyPressed(keyEvent(KeyEvent.KEY_PRESSED,   KeyEvent.VK_RIGHT));
        player.keyReleased(keyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_UP)); // not bound
        assertEquals(PongAction.RIGHT, player.getNextMove(DUMMY_STATE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // keyTyped — always a no-op
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("keyTyped does not affect the current action")
    void keyTypedIsNoOp() {
        player.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT));
        player.keyTyped(keyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED));
        assertEquals(PongAction.LEFT, player.getNextMove(DUMMY_STATE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Metadata
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getName() returns the name passed to the constructor")
    void getNameReturnsConstructorValue() {
        assertEquals("P1", player.getName());
    }

    @Test
    @DisplayName("getType() returns PlayerType.HUMAN")
    void getTypeReturnsHuman() {
        assertEquals(PlayerType.HUMAN, player.getType());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Edge cases
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Player with empty bindings always returns the default action")
    void emptyBindingsAlwaysDefault() {
        HumanPlayer<PongState, PongAction> p = new HumanPlayer<>("P2", Map.of(), PongAction.IDLE);
        p.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT));
        assertEquals(PongAction.IDLE, p.getNextMove(DUMMY_STATE));
    }

    @Test
    @DisplayName("getNextMove() ignores the state argument and returns current action")
    void getNextMoveIgnoresState() {
        player.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_RIGHT));
        PongState state1 = new PongState(0,   0, 0, 600);
        PongState state2 = new PongState(500, 0, 0, 600);
        assertEquals(PongAction.RIGHT, player.getNextMove(state1));
        assertEquals(PongAction.RIGHT, player.getNextMove(state2));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────────────

    private KeyEvent keyEvent(int id, int keyCode) {
        return new KeyEvent(src, id, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }
}
