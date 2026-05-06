package edu.sjsu.spring2026.group32.hitthezone.ui;

import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneSoftwareAI;
import edu.sjsu.spring2026.group32.hitthezone.core.HitTheZoneEngine;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;

@DisplayName("HitTheZoneHudPanel Suite")
class HitTheZoneHudPanelTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private HitTheZoneHudPanel panel;
    private HitTheZoneSoftwareAI playerA;
    private HitTheZoneSoftwareAI playerB;

    @BeforeEach
    void setUp() {
        panel = new HitTheZoneHudPanel(null); // null manager — no serial connection needed
        playerA = new HitTheZoneSoftwareAI("Alice", 0);
        playerB = new HitTheZoneSoftwareAI("Bob", 0);
        panel.syncPlayers(List.of(playerA, playerB));
    }

    // syncPlayers

    @Test
    @DisplayName("syncPlayers() creates one label per player")
    void refreshUpdatesDisplayedHudState_syncPlayersCreatesLabels() {
        assertEquals(2, panel.playerLabels.length, "should have one label per player");
    }

    @Test
    @DisplayName("syncPlayers() replaces labels on second call — no accumulation")
    void refreshUpdatesDisplayedHudState_syncPlayersRefreshNoDuplicates() {
        panel.syncPlayers(List.of(playerA)); // re-sync with one player
        assertEquals(1, panel.playerLabels.length, "re-sync should replace labels, not accumulate");
    }

    @Test
    @DisplayName("syncPlayers() with empty list produces no labels")
    void refreshUpdatesDisplayedHudState_syncPlayersEmpty() {
        panel.syncPlayers(List.of());
        assertEquals(0, panel.playerLabels.length, "empty player list should produce no labels");
    }

    // refresh 

    @Test
    @DisplayName("refresh() shows player name and type in each label")
    void refreshUpdatesDisplayedHudState_labelContainsNameAndType() {
        HitTheZoneSnapshot snapshot = snapshotWith(new int[]{0, 0}, new int[]{0, 0}, 0);

        panel.refresh(snapshot, List.of(playerA, playerB), "");

        assertTrue(panel.playerLabels[0].getText().contains("Alice"), "first label should contain player A name");
        assertTrue(panel.playerLabels[1].getText().contains("Bob"), "second label should contain player B name");
        assertTrue(panel.playerLabels[0].getText().contains("SOFTWARE"), "label should contain player type");
    }

    @Test
    @DisplayName("refresh() shows correct hit and attempt counts")
    void refreshUpdatesDisplayedHudState_labelShowsHitsAndAttempts() {
        HitTheZoneSnapshot snapshot = snapshotWith(new int[]{3, 1}, new int[]{5, 2}, 10);

        panel.refresh(snapshot, List.of(playerA, playerB), "");

        assertTrue(panel.playerLabels[0].getText().contains("3 / 5"), "first label should show 3 hits out of 5 attempts");
        assertTrue(panel.playerLabels[1].getText().contains("1 / 2"), "second label should show 1 hit out of 2 attempts");
    }

    @Test
    @DisplayName("refresh() shows dash for accuracy when no attempts made")
    void refreshUpdatesDisplayedHudState_noAttemptShowsDash() {
        HitTheZoneSnapshot snapshot = snapshotWith(new int[]{0, 0}, new int[]{0, 0}, 0);

        panel.refresh(snapshot, List.of(playerA, playerB), "");

        // accuracy field should show "-" when attempts == 0
        String text = panel.playerLabels[0].getText();

        assertTrue(text.contains("Accuracy: -"), "accuracy should show dash when no attempts");

    }

    @Test
    @DisplayName("refresh() shows dash for hits/pass when no passes occurred")
    void refreshUpdatesDisplayedHudState_noPassesShowsDash() {
        HitTheZoneSnapshot snapshot = snapshotWith(new int[]{0, 0}, new int[]{0, 0}, 0);

        panel.refresh(snapshot, List.of(playerA, playerB), "");

        assertTrue(panel.playerLabels[0].getText().contains("Hits/Pass: -"), "hits/pass should show dash when no passes");
    }

    @Test
    @DisplayName("refresh() calculates accuracy percentage correctly")
    void refreshUpdatesDisplayedHudState_accuracyPercentage() {
        HitTheZoneSnapshot snapshot = snapshotWith(new int[]{2, 0}, new int[]{4, 0}, 10);

        panel.refresh(snapshot, List.of(playerA, playerB), "");

        assertTrue(panel.playerLabels[0].getText().contains("50%"), "accuracy should be 50% for 2 hits out of 4 attempts");
    }

    @Test
    @DisplayName("refresh() calculates hits/pass ratio correctly")
    void refreshUpdatesDisplayedHudState_hitsPerPass() {
        HitTheZoneSnapshot snapshot = snapshotWith(new int[]{3, 0}, new int[]{4, 0}, 6);

        panel.refresh(snapshot, List.of(playerA, playerB), "");

        assertTrue(panel.playerLabels[0].getText().contains("0.50"), "hits/pass should be 0.50 for 3 hits over 6 passes");
    }

    // refresh, info label

    @Test
    @DisplayName("refresh() sets the info label text")
    void refreshUpdatesDisplayedHudState_infoLabelUpdated() {
        HitTheZoneSnapshot snapshot = snapshotWith(new int[]{0, 0}, new int[]{0, 0}, 0);

        panel.refresh(snapshot, List.of(playerA, playerB), "Time: 01:23");

        assertEquals("Time: 01:23", panel.infoLabel.getText(), "info label should reflect the supplied info text");
    }

    @Test
    @DisplayName("refresh() with empty info text clears the info label")
    void refreshUpdatesDisplayedHudState_emptyInfoText() {
        HitTheZoneSnapshot snapshot = snapshotWith(new int[]{0, 0}, new int[]{0, 0}, 0);

        panel.refresh(snapshot, List.of(playerA, playerB), "");

        assertEquals("", panel.infoLabel.getText(), "info label should be empty when info text is empty");
    }

    @Test
    @DisplayName("refresh() with override/paused info text shows override message")
    void refreshUpdatesDisplayedHudState_overrideInfoText() {
        HitTheZoneSnapshot snapshot = snapshotWith(new int[]{0, 0}, new int[]{0, 0}, 0);

        panel.refresh(snapshot, List.of(playerA, playerB), "--- PAUSED  (Esc to resume | R to reset) ---");

        assertTrue(panel.infoLabel.getText().contains("PAUSED"), "info label should show override paused message");
    }

    // helper — builds a minimal HitTheZoneSnapshot

    private static HitTheZoneSnapshot snapshotWith(int[] hits, int[] attempts, int totalPasses) {
        return new HitTheZoneSnapshot(
                HitTheZoneEngine.START_X, // ballX
                HitTheZoneEngine.DEFAULT_BALL_SPEED, // direction
                totalPasses,
                false, // inZone
                false, // paused
                0L, // elapsedMs
                HitTheZoneEngine.DEFAULT_BALL_SPEED,
                HitTheZoneEngine.DEFAULT_ZONE_WIDTH,
                hits,
                attempts);
    
    }
}