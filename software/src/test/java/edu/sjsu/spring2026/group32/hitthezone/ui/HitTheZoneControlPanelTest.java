package edu.sjsu.spring2026.group32.hitthezone.ui;

import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneSoftwareAI;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;
import edu.sjsu.spring2026.group32.player.HumanPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;

@DisplayName("HitTheZoneControlPanel Suite")
class HitTheZoneControlPanelTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private HitTheZoneControlPanel panel;
    private List<String> pauseEvents;
    private List<String> resetEvents;
    private List<Integer> scoreEvents;

    @BeforeEach
    void setUp() {
        pauseEvents = new ArrayList<>();
        resetEvents = new ArrayList<>();
        scoreEvents = new ArrayList<>();

        panel = new HitTheZoneControlPanel(() -> pauseEvents.add("pause"), () -> resetEvents.add("reset"), scoreEvents::add);
    }

    // helper, fires a button's first ActionListener

    private static void click(javax.swing.JButton button) {
        for (var l : button.getActionListeners()) {
            l.actionPerformed(new java.awt.event.ActionEvent(button, java.awt.event.ActionEvent.ACTION_PERFORMED, "click"));
        }
    }

    // pause button

    @Test
    @DisplayName("clicking pause button fires onPause callback")
    void controlsTriggerExpectedCallbacks_pauseButton() {
        click(panel.pauseButton);

        assertEquals(1, pauseEvents.size(), "onPause should fire once");
        assertTrue(resetEvents.isEmpty(), "onReset should not fire");

    }

    @Test
    @DisplayName("clicking pause button multiple times fires callback each time")
    void controlsTriggerExpectedCallbacks_pauseButtonMultipleTimes() {
        click(panel.pauseButton);
        click(panel.pauseButton);
        click(panel.pauseButton);

        assertEquals(3, pauseEvents.size(), "onPause should fire on each click");
    }

    // reset button

    @Test
    @DisplayName("clicking reset button fires onReset callback")
    void controlsTriggerExpectedCallbacks_resetButton() {
        click(panel.resetButton);

        assertEquals(1, resetEvents.size(), "onReset should fire once");
        assertTrue(pauseEvents.isEmpty(), "onPause should not fire");
    }

    // setPaused

    @Test
    @DisplayName("setPaused(true) changes pause button text to Resume")
    void controlsTriggerExpectedCallbacks_setPausedChangesText() {
        panel.setPaused(true);
        assertEquals("Resume (Esc)", panel.pauseButton.getText(), "pause button should say Resume when paused");

        panel.setPaused(false);
        assertEquals("Pause (Esc)", panel.pauseButton.getText(), "pause button should say Pause when unpaused");
    }

    // syncPlayers, human players get score buttons

    @Test
    @DisplayName("syncPlayers() adds score buttons for human (KeyListener) players only")
    void controlsTriggerExpectedCallbacks_syncPlayersAddsHumanButtons() {
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human = new HumanPlayer<>("Alice", Map.of(java.awt.event.KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);

        HitTheZoneSoftwareAI software = new HitTheZoneSoftwareAI("Bot", 0);

        panel.syncPlayers(List.of(human, software));

        assertEquals(1, panel.humanScoreButtons.size(), "only the human player should get a score button");

        assertTrue(panel.humanScoreButtons.get(0).getText().contains("Alice"), "score button should include the player name");
    }

    @Test
    @DisplayName("clicking a score button fires onScore with the correct player index")
    void controlsTriggerExpectedCallbacks_scoreButtonFiresWithCorrectIndex() {
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human0 = new HumanPlayer<>("Alice", Map.of(java.awt.event.KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human1 = new HumanPlayer<>("Bob", Map.of(java.awt.event.KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);

        panel.syncPlayers(List.of(human0, human1));

        click(panel.humanScoreButtons.get(0));
        click(panel.humanScoreButtons.get(1));

        assertEquals(List.of(0, 1), scoreEvents, "score events should fire with player indices 0 and 1");
    }

    @Test
    @DisplayName("syncPlayers() with no human players adds no score buttons")
    void controlsTriggerExpectedCallbacks_syncPlayersNoHumans() {
        HitTheZoneSoftwareAI bot = new HitTheZoneSoftwareAI("Bot", 0);
        panel.syncPlayers(List.of(bot));

        assertTrue(panel.humanScoreButtons.isEmpty(), "no score buttons should be added for software players");
    }

    @Test
    @DisplayName("syncPlayers() replaces buttons on second call — no duplicates")
    void controlsTriggerExpectedCallbacks_syncPlayersRefreshNoDuplicates() {
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human = new HumanPlayer<>("Alice", Map.of(java.awt.event.KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);

        panel.syncPlayers(List.of(human));
        panel.syncPlayers(List.of(human)); 

        assertEquals(1, panel.humanScoreButtons.size(), "second syncPlayers call should not duplicate buttons");
    }

    // setPaused disables score buttons

    @Test
    @DisplayName("setPaused(true) disables score buttons for human players")
    void controlsTriggerExpectedCallbacks_setPausedDisablesScoreButtons() {
        HumanPlayer<HitTheZoneState, HitTheZoneAction> human = new HumanPlayer<>("Alice", Map.of(java.awt.event.KeyEvent.VK_SPACE, HitTheZoneAction.SCORE), null);
        
        panel.syncPlayers(List.of(human));

        panel.setPaused(true);

        assertFalse(panel.humanScoreButtons.get(0).isEnabled(), "score button should be disabled when paused");

        panel.setPaused(false);
        assertTrue(panel.humanScoreButtons.get(0).isEnabled(), "score button should be re-enabled when unpaused");

    }
}