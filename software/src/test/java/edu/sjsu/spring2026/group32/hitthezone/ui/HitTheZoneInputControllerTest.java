package edu.sjsu.spring2026.group32.hitthezone.ui;

import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneSoftwareAI;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;
import edu.sjsu.spring2026.group32.player.HumanPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.junit.jupiter.api.BeforeEach;

@DisplayName("HitTheZoneInputController Suite")
class HitTheZoneInputControllerTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private HitTheZoneInputController controller;
    private JComponent component;
    private List<String> pauseEvents;
    private List<String> resetEvents;

    @BeforeEach
    void setUp() {
        controller = new HitTheZoneInputController();
        component = new JPanel();
        pauseEvents = new ArrayList<>();
        resetEvents = new ArrayList<>();

        controller.bindKeys(component, () -> pauseEvents.add("pause"), () -> resetEvents.add("reset"));
    }

    // helper

    private void fire(String actionKey) {
        var action = component.getActionMap().get(actionKey);
        action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, actionKey));
    }

    // bindKeys (ESC and R)

    @Test
    @DisplayName("ESC fires onPause callback")
    void keyBindings_Cases_escFiresPause() {
        fire("pauseAction");

        assertEquals(1, pauseEvents.size(), "ESC should fire onPause once");
        assertTrue(resetEvents.isEmpty(),   "onReset should not fire");
    }

    @Test
    @DisplayName("R fires onReset callback")
    void keyBindings_Cases_rFiresReset() {
        fire("resetAction");

        assertEquals(1, resetEvents.size(), "R should fire onReset once");
        assertTrue(pauseEvents.isEmpty(),   "onPause should not fire");
    }

    @Test
    @DisplayName("ESC and R can fire independently multiple times")
    void keyBindings_Cases_multipleFiresIndependent() {
        fire("pauseAction");
        fire("pauseAction");
        fire("resetAction");

        assertEquals(2, pauseEvents.size(), "onPause should fire twice");
        assertEquals(1, resetEvents.size(), "onReset should fire once");
    }

    @Test
    @DisplayName("pause and reset actions are registered in the ActionMap")
    void keyBindings_Cases_actionsRegistered() {
        assertTrue(component.getActionMap().get("pauseAction") != null, "pauseAction should be registered");
        assertTrue(component.getActionMap().get("resetAction") != null, "resetAction should be registered");
    }

    // registerHumanPlayers

    @Test
    @DisplayName("registerHumanPlayers() adds a dispatcher for each KeyListener player")
    void keyBindings_Cases_registerAddsDispatchers() {
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human = new HumanPlayer<>("Alice", Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);

        controller.registerHumanPlayers(List.of(human));

        assertEquals(1, controller.dispatchers.size(), "one dispatcher should be registered for one human player");
    }

    @Test
    @DisplayName("registerHumanPlayers() ignores non-KeyListener players")
    void keyBindings_Cases_nonKeyListenerIgnored() {
        HitTheZoneSoftwareAI software = new HitTheZoneSoftwareAI("Bot", 0);

        controller.registerHumanPlayers(List.of(software));

        assertTrue(controller.dispatchers.isEmpty(), "software AI should not register a dispatcher");
    }

    @Test
    @DisplayName("registerHumanPlayers() with mixed players only registers human ones")
    void keyBindings_Cases_mixedPlayersOnlyRegistersHumans() {
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human = new HumanPlayer<>("Alice", Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);
        HitTheZoneSoftwareAI software = new HitTheZoneSoftwareAI("Bot", 0);

        controller.registerHumanPlayers(List.of(human, software));

        assertEquals(1, controller.dispatchers.size(), "only the human player should register a dispatcher");
    }

    @Test
    @DisplayName("registerHumanPlayers() called twice replaces previous dispatchers")
    void keyBindings_Cases_duplicateRegistrationReplaces() {
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human = new HumanPlayer<>("Alice", Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);

        controller.registerHumanPlayers(List.of(human));
        controller.registerHumanPlayers(List.of(human)); // second call

        assertEquals(1, controller.dispatchers.size(), "second register should replace, not accumulate dispatchers");
    }

    // unregisterHumanPlayers

    @Test
    @DisplayName("unregisterHumanPlayers() clears all dispatchers")
    void keyBindings_Cases_unregisterClearsDispatchers() {
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human = new HumanPlayer<>("Alice", Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);

        controller.registerHumanPlayers(List.of(human));
        assertEquals(1, controller.dispatchers.size());

        controller.unregisterHumanPlayers();

        assertTrue(controller.dispatchers.isEmpty(), "dispatchers should be empty after unregister");
    }

    @Test
    @DisplayName("unregisterHumanPlayers() is safe when nothing is registered")
    void keyBindings_Cases_unregisterWhenEmptyIsSafe() {

        controller.unregisterHumanPlayers();
        assertTrue(controller.dispatchers.isEmpty());
    }

    // Dispatcher forwards key events to human player

    @Test
    @DisplayName("dispatcher forwards KEY_PRESSED events to human player")
    void keyBindings_Cases_dispatcherForwardsKeyPressed() {
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human = new HumanPlayer<>("Alice",  Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);

        controller.registerHumanPlayers(List.of(human));

        // simulates KEY_PRESSED via the dispatcher directly
        KeyEvent pressed = new KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, KeyEvent.CHAR_UNDEFINED);

        controller.dispatchers.get(0).dispatchKeyEvent(pressed);

        assertEquals(HitTheZoneAction.SCORE, human.getNextMove(new HitTheZoneState(false)), "human player should report SCORE after SPACE is pressed");
    }

    @Test
    @DisplayName("dispatcher forwards KEY_RELEASED events to human player")
    void keyBindings_Cases_dispatcherForwardsKeyReleased() {
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human = new HumanPlayer<>("Alice", Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);

        controller.registerHumanPlayers(List.of(human));

        // press then release
        KeyEvent pressed = new KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, KeyEvent.CHAR_UNDEFINED);
        KeyEvent released = new KeyEvent(component, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, KeyEvent.CHAR_UNDEFINED);

        controller.dispatchers.get(0).dispatchKeyEvent(pressed);
        controller.dispatchers.get(0).dispatchKeyEvent(released);

        assertEquals(null, human.getNextMove(new HitTheZoneState(false)), "human player should revert to null (default) after SPACE is released");
    }
}