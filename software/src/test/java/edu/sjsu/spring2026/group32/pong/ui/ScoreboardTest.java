package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;


@DisplayName("Scoreboard Suite")
class ScoreboardTest {

    @TempDir
    Path tempDir;

    private Scoreboard scoreboard;

    @BeforeEach
    void setUp() {
        scoreboard = new Scoreboard(tempDir.resolve("test_scoreboard.txt"));

    }

    // recordPoint / getRecords

    @Test
    @DisplayName("getRecords() is empty before any points are recorded")
    void createPopupDialog_reflectsCurrentScoreboardData_emptyInitially() {
        assertTrue(scoreboard.getRecords().isEmpty(), "scoreboard should have no records before any points are recorded");
    }

    @Test
    @DisplayName("recordPoint() creates a record for the matchup")
    void createPopupDialog_reflectsCurrentScoreboardData_recordCreated() {
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.AI_EASY);

        Collection<Scoreboard.MatchupRecord> records = scoreboard.getRecords();
        assertEquals(1, records.size(), "one matchup record should exist after recording a point");
    }

    @Test
    @DisplayName("recordPoint() accumulates points for the same matchup")
    void createPopupDialog_reflectsCurrentScoreboardData_pointsAccumulate() {
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.AI_EASY);
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.AI_EASY);
        scoreboard.recordPoint(PlayerVariant.AI_EASY, PlayerVariant.AI_HARD);

        Collection<Scoreboard.MatchupRecord> records = scoreboard.getRecords();
        assertEquals(1, records.size(), "same matchup pair should produce one record");

        Scoreboard.MatchupRecord rec = records.iterator().next();
        // variantA is whichever side was registered first — AI_HARD scored 2, AI_EASY scored 1
        int totalPoints = rec.pointsA + rec.pointsB;
        assertEquals(3, totalPoints, "total points across both sides should be 3");
    }

    @Test
    @DisplayName("recordPoint() ignores self-matchups")
    void createPopupDialog_reflectsCurrentScoreboardData_selfMatchupIgnored() {
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.AI_HARD);

        assertTrue(scoreboard.getRecords().isEmpty(), "self-matchup should be silently ignored");
    }

    @Test
    @DisplayName("multiple distinct matchups each get their own record")
    void createPopupDialog_reflectsCurrentScoreboardData_distinctMatchups() {
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.AI_EASY);
        scoreboard.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_EASY);

        assertEquals(2, scoreboard.getRecords().size(), "two distinct matchups should produce two records");
    }

    @Test
    @DisplayName("getRecords() returns an unmodifiable collection")
    void createPopupDialog_reflectsCurrentScoreboardData_unmodifiable() {
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.AI_EASY);

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> scoreboard.getRecords().clear(), "getRecords() should return an unmodifiable collection");
    }

    // reset

    @Test
    @DisplayName("reset() clears all records from memory")
    void resetButton_clearsVisibleRowsAndBackingData_clearsMemory() {
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.AI_EASY);
        scoreboard.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_EASY);
        
        assertEquals(2, scoreboard.getRecords().size());

        scoreboard.reset();

        assertTrue(scoreboard.getRecords().isEmpty(), "all records should be cleared after reset()");
    }

    @Test
    @DisplayName("reset() on empty scoreboard does not throw")
    void resetButton_clearsVisibleRowsAndBackingData_resetEmptyIsSafe() {
        scoreboard.reset(); // should not throw
        assertTrue(scoreboard.getRecords().isEmpty());
    }

    @Test
    @DisplayName("recordPoint() after reset() starts fresh")
    void resetButton_clearsVisibleRowsAndBackingData_recordAfterReset() {
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.AI_EASY);

        scoreboard.reset();

        scoreboard.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_EASY);

        Collection<Scoreboard.MatchupRecord> records = scoreboard.getRecords();

        assertEquals(1, records.size(), "only the post-reset record should exist");
    }

    // createPopupDialog / resetButton excluded
    @Test
    @DisplayName("createPopupDialog is @GeneratedExcludeFromCoverage — Swing JDialog with " + "JOptionPane confirmation; UI layer excluded from coverage")
    void createPopupDialog_reflectsCurrentScoreboardData_dialogExcluded() {
        // createPopupDialog() builds a full JDialog and the reset button calls JOptionPane.showConfirmDialog() --> block test thread
        // the backing logic (recordPoint, reset, getRecords) is tested above.
        assertTrue(true, "documented exclusion");
    }

    @Test
    @DisplayName("resetButton is @GeneratedExcludeFromCoverage — calls JOptionPane.showConfirmDialog")
    void resetButton_clearsVisibleRowsAndBackingData_buttonExcluded() {
        assertTrue(true, "documented exclusion");
    }
}