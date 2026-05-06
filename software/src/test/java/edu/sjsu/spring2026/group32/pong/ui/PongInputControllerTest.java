package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.player.HumanPlayer;
import edu.sjsu.spring2026.group32.pong.model.PongAction;
import edu.sjsu.spring2026.group32.pong.model.PongState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.junit.jupiter.api.BeforeEach;

@DisplayName("PongInputController Suite")
class PongInputControllerTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private PongInputController controller;
    private JComponent          component;

    // Callback capture lists
    private List<String>  pauseEvents;
    private List<String>  resetEvents;
    private List<String>  constSpeedEvents;
    private List<Integer> speedEvents;
    private List<String>  refreshEvents;

    // Pause menu state — controls guarded actions
    private boolean pauseMenuActive;

    @BeforeEach
    void setUp() {
        controller = new PongInputController();
        component = new JPanel();
        pauseEvents = new ArrayList<>();
        resetEvents = new ArrayList<>();
        constSpeedEvents = new ArrayList<>();
        speedEvents = new ArrayList<>();
        refreshEvents = new ArrayList<>();
        pauseMenuActive = true;

        controller.bindGameKeys(
                component,
                () -> pauseEvents.add("pause"),
                () -> resetEvents.add("reset"),
                () -> constSpeedEvents.add("constSpeed"),
                speedEvents::add,
                () -> pauseMenuActive,
                () -> refreshEvents.add("refresh"));
    }

    // helper, fires an action directly from the ActionMap

    private void fire(String actionKey) {
        var action = component.getActionMap().get(actionKey);
        assertNotNull(action, "action '" + actionKey + "' should be registered");

        action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, actionKey));
    }

    // ESC, togglePause

    @Test
    @DisplayName("ESC fires togglePause regardless of pause menu state")
    void keyBindings_escTogglesPause() {
        fire("game-esc");

        assertEquals(1, pauseEvents.size(), "ESC should fire togglePause");

        pauseMenuActive = false;
        fire("game-esc");

        assertEquals(2, pauseEvents.size(), "ESC should always fire regardless of guard");
    }

    // R, reset

    @Test
    @DisplayName("R fires reset when pause menu is active")
    void keyBindings_resetWhenGuardPasses() {
        pauseMenuActive = true;

        fire("game-reset");
        assertEquals(1, resetEvents.size(), "R should fire reset when guard passes");
    }

    @Test
    @DisplayName("R does not fire reset when pause menu is inactive")
    void keyBindings_resetBlockedWhenGuardFails() {
        pauseMenuActive = false;

        fire("game-reset");
        assertEquals(0, resetEvents.size(), "R should not fire reset when guard fails");
    }

    // C, toggleConstantSpeed (guarded, also calls refresh)

    @Test
    @DisplayName("C fires toggleConstantSpeed and refresh when pause menu is active")
    void keyBindings_constSpeedWhenGuardPasses() {
        pauseMenuActive = true;

        fire("game-const-speed");
        assertEquals(1, constSpeedEvents.size(), "C should fire toggleConstantSpeed");
        assertEquals(1, refreshEvents.size(), "C should fire refresh");
    }

    @Test
    @DisplayName("C does not fire toggleConstantSpeed when pause menu is inactive")
    void keyBindings_constSpeedBlockedWhenGuardFails() {
        pauseMenuActive = false;

        fire("game-const-speed");
        assertEquals(0, constSpeedEvents.size(), "C should not fire when guard fails");
        assertEquals(0, refreshEvents.size(), "refresh should not fire when guard fails");
    }

    // 1 / 2 / 3, speed selection (guarded, also calls refresh)

    @Test
    @DisplayName("1/2/3 fire correct speed level and refresh when guard passes")
    void keyBindings_speedKeys() {
        pauseMenuActive = true;

        fire("speed-1");
        fire("speed-2");
        fire("speed-3");

        assertEquals(List.of(0, 1, 2), speedEvents, "speed keys should fire levels 0, 1, 2 in order");
        assertEquals(3, refreshEvents.size(), "each speed key should also fire refresh");
    }

    @Test
    @DisplayName("speed keys do not fire when pause menu is inactive")
    void keyBindings_speedKeysBlockedWhenGuardFails() {
        pauseMenuActive = false;

        fire("speed-1");
        fire("speed-2");
        fire("speed-3");

        assertEquals(0, speedEvents.size(), "speed keys should not fire when guard fails");
        assertEquals(0, refreshEvents.size(), "refresh should not fire when guard fails");
    }


    @Test
    @DisplayName("wireHumanPlayer() registers LEFT and RIGHT press/release actions")
    void keyBindings_humanPlayerWiring() {
        HumanPlayer<PongState, PongAction> human = new HumanPlayer<>(
                "TestHuman",
                java.util.Map.of(
                        java.awt.event.KeyEvent.VK_LEFT,  PongAction.LEFT,
                        java.awt.event.KeyEvent.VK_RIGHT, PongAction.RIGHT),
                PongAction.IDLE);

        controller.wireHumanPlayer(component, human);

        assertNotNull(component.getActionMap().get("hp-L-dn"), "LEFT press should be bound");
        assertNotNull(component.getActionMap().get("hp-L-up"), "LEFT release should be bound");
        assertNotNull(component.getActionMap().get("hp-R-dn"), "RIGHT press should be bound");
        assertNotNull(component.getActionMap().get("hp-R-up"), "RIGHT release should be bound");

        fire("hp-L-dn");
        assertEquals(PongAction.LEFT, human.getNextMove(new PongState(0, 0, 0, 0, 0)), "LEFT press should set human action to LEFT");

        fire("hp-L-up");
        assertEquals(PongAction.IDLE, human.getNextMove(new PongState(0, 0, 0, 0, 0)), "LEFT release should revert human action to IDLE");

        fire("hp-R-dn");
        assertEquals(PongAction.RIGHT, human.getNextMove(new PongState(0, 0, 0, 0, 0)), "RIGHT press should set human action to RIGHT");

        fire("hp-R-up");
        assertEquals(PongAction.IDLE, human.getNextMove(new PongState(0, 0, 0, 0, 0)), "RIGHT release should revert human action to IDLE");
    }

    @Test
    @DisplayName("wireHumanPlayer(null) clears existing human player bindings")
    void keyBindings_wireNullHumanPlayer() {
        HumanPlayer<PongState, PongAction> human = new HumanPlayer<>(
                "TestHuman",
                java.util.Map.of(
                        java.awt.event.KeyEvent.VK_LEFT,  PongAction.LEFT,
                        java.awt.event.KeyEvent.VK_RIGHT, PongAction.RIGHT),
                PongAction.IDLE);

        controller.wireHumanPlayer(component, human);
        controller.wireHumanPlayer(component, null); // should unbind

        assertNull(component.getActionMap().get("hp-L-dn"), "LEFT press should be unbound");
        assertNull(component.getActionMap().get("hp-L-up"), "LEFT release should be unbound");

        assertNull(component.getActionMap().get("hp-R-dn"), "RIGHT press should be unbound");
        assertNull(component.getActionMap().get("hp-R-up"), "RIGHT release should be unbound");
    }

    // unbindHumanKeys

    @Test
    @DisplayName("unbindHumanKeys() removes all human player action bindings")
    void keyBindings_unbindHumanKeys() {
        HumanPlayer<PongState, PongAction> human = new HumanPlayer<>(
                "TestHuman",
                java.util.Map.of(
                        java.awt.event.KeyEvent.VK_LEFT,  PongAction.LEFT,
                        java.awt.event.KeyEvent.VK_RIGHT, PongAction.RIGHT),
                PongAction.IDLE);

        controller.wireHumanPlayer(component, human);
        controller.unbindHumanKeys(component);

        assertNull(component.getActionMap().get("hp-L-dn"), "hp-L-dn should be removed");
        assertNull(component.getActionMap().get("hp-L-up"), "hp-L-up should be removed");

        assertNull(component.getActionMap().get("hp-R-dn"), "hp-R-dn should be removed");
        assertNull(component.getActionMap().get("hp-R-up"), "hp-R-up should be removed");
    }

    @Test
    @DisplayName("unbindHumanKeys() is safe to call when no human player is wired")
    void keyBindings_unbindWhenNothingBound() {
        controller.unbindHumanKeys(component);
        assertNull(component.getActionMap().get("hp-L-dn"));

    }
}