package edu.sjsu.spring2026.group32.hitthezone;

import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneSoftwareAI;
import edu.sjsu.spring2026.group32.hitthezone.core.HitTheZoneEngine;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneActionEffect;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneSnapshot;
import edu.sjsu.spring2026.group32.player.model.PlayerType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


import org.junit.jupiter.api.BeforeEach;

/**
 * Tests the game logic delegated to by HitTheZoneGame.
 *
 * HitTheZoneGame is a Swing JFrame shell (@GeneratedExcludeFromCoverage)
 * whose logic lives entirely in HitTheZoneEngine. These tests cover that
 * logic directly, matching the paths exercised by onTick(), processScore(),
 * resetGame(), setBallSpeed(), and setZoneWidth().
 */
@DisplayName("HitTheZoneGame Suite")
class HitTheZoneGameTest {

    private HitTheZoneEngine engine;
    @BeforeEach
    void setUp() {
        new HitTheZoneSoftwareAI("Software", 0);
        engine = new HitTheZoneEngine(1);
    }

    // gameplay loop — mirrors onTick() delegation

    @Test
    @DisplayName("tick advances elapsed time and moves ball when unpaused")
    void gameplayLoop_coversPositiveAndNegativeRuntimeCases_tickAdvancesState() {
        HitTheZoneSnapshot before = engine.snapshot();
        assertFalse(before.paused(), "engine should start unpaused");

        engine.tickMotion(HitTheZoneEngine.WIDTH);

        HitTheZoneSnapshot after = engine.snapshot();
        assertEquals(before.elapsedMs() + 16, after.elapsedMs(), "each tick should advance elapsed time by 16 ms");
        assertTrue(after.ballX() != before.ballX(), "ball should move each tick");
    }

    @Test
    @DisplayName("tick does nothing when paused")
    void gameplayLoop_coversPositiveAndNegativeRuntimeCases_tickNoOpWhenPaused() {
        engine.togglePause(); // pause it
        assertTrue(engine.isPaused());

        HitTheZoneSnapshot before = engine.snapshot();
        engine.tickMotion(HitTheZoneEngine.WIDTH);
        HitTheZoneSnapshot after = engine.snapshot();

        assertEquals(before.elapsedMs(), after.elapsedMs(), "elapsed time should not advance while paused");
        assertEquals(before.ballX(), after.ballX(), "ball should not move while paused");
    }

    @Test
    @DisplayName("processScore() increments attempts when ball is in zone")
    void gameplayLoop_coversPositiveAndNegativeRuntimeCases_processScore() {
        for (int i = 0; i < 1000; i++) {
            engine.tickMotion(HitTheZoneEngine.WIDTH);
            if (engine.snapshot().inZone()) break;
        }
        assertTrue(engine.snapshot().inZone(), "ball should reach the zone within 1000 ticks");

        int attemptsBefore = engine.snapshot().attempts()[0];
        engine.handleAction(0, HitTheZoneAction.SCORE, PlayerType.SOFTWARE, false);

        assertEquals(attemptsBefore + 1, engine.snapshot().attempts()[0], "processScore() should increment attempts for the player");
    }

    @Test
    @DisplayName("processScore() while paused has no effect")
    void gameplayLoop_coversPositiveAndNegativeRuntimeCases_processScoreWhilePaused() {
        engine.togglePause(); // pause it
        int attemptsBefore = engine.snapshot().attempts()[0];

        engine.handleAction(0, HitTheZoneAction.SCORE, PlayerType.SOFTWARE, false);

        assertEquals(attemptsBefore, engine.snapshot().attempts()[0], "score attempt while paused should be ignored");
    }

    @Test
    @DisplayName("resetGame() clears elapsed time, total passes, and ball position")
    void gameplayLoop_coversPositiveAndNegativeRuntimeCases_resetGame() {
        for (int i = 0; i < 10; i++) engine.tickMotion(HitTheZoneEngine.WIDTH);

        engine.resetMatch();

        HitTheZoneSnapshot snap = engine.snapshot();
        assertEquals(0, snap.elapsedMs(), "elapsed time should reset");
        assertEquals(0, snap.totalPasses(), "total passes should reset");

        assertEquals(HitTheZoneEngine.START_X, snap.ballX(), "ball should reset to start");

        assertEquals(0, snap.attempts()[0], "attempts should reset");
        assertEquals(0, snap.hits()[0], "hits should reset");

    }

    @Test
    @DisplayName("resetMatch() preserves pause state — game calls togglePause separately")
    void gameplayLoop_coversPositiveAndNegativeRuntimeCases_resetUnpauses() {
        engine.togglePause(); // pause it
        assertTrue(engine.isPaused());

        engine.resetMatch();

        HitTheZoneSnapshot snap = engine.snapshot();
        assertTrue(snap.paused(), "pause state should be preserved by resetMatch");

        assertEquals(0, snap.elapsedMs(), "elapsed time should reset");
        assertEquals(0, snap.totalPasses(), "total passes should reset");

        assertEquals(HitTheZoneEngine.START_X, snap.ballX(), "ball should reset");

    }

    @Test
    @DisplayName("PAUSE action from player triggers pause effect")
    void gameplayLoop_coversPositiveAndNegativeRuntimeCases_pauseAction() {
        HitTheZoneActionEffect effect = engine.handleAction( 0, HitTheZoneAction.PAUSE, PlayerType.SOFTWARE, false);
        assertEquals(HitTheZoneActionEffect.PAUSE_REQUESTED, effect, "PAUSE action should return PAUSE_REQUESTED effect");
    }

    @Test
    @DisplayName("RESET action from player triggers reset effect")
    void gameplayLoop_coversPositiveAndNegativeRuntimeCases_resetAction() {
        HitTheZoneActionEffect effect = engine.handleAction(0, HitTheZoneAction.RESET, PlayerType.SOFTWARE, false);
        assertEquals(HitTheZoneActionEffect.RESET_REQUESTED, effect, "RESET action should return RESET_REQUESTED effect");
    }

    // Settings, mirrors setBallSpeed() and setZoneWidth() delegation

    @Test
    @DisplayName("setBallSpeed() updates engine speed")
    void gameplaySettings_updateEngineAndUiState_ballSpeed() {
        engine.setBallSpeed(10);
        assertEquals(10, engine.snapshot().ballSpeed(), "engine ball speed should reflect the new value");
    }

    @Test
    @DisplayName("setZoneWidth() updates engine zone width")
    void gameplaySettings_updateEngineAndUiState_zoneWidth() {
        engine.setZoneWidth(200);
        assertEquals(200, engine.snapshot().zoneWidth(), "engine zone width should reflect the new value");
    }

    @Test
    @DisplayName("setBallSpeed() with zero or negative value throws, engine unchanged")
    void gameplaySettings_updateEngineAndUiState_invalidBallSpeed() {
        int before = engine.snapshot().ballSpeed();

        assertThrows(IllegalArgumentException.class, () -> engine.setBallSpeed(0),  "speed=0 should throw");
        assertThrows(IllegalArgumentException.class, () -> engine.setBallSpeed(-1), "negative speed should throw");

        assertEquals(before, engine.snapshot().ballSpeed(), "engine speed should be unchanged after invalid input");
    }

    @Test
    @DisplayName("setZoneWidth() with out-of-bounds value throws, engine unchanged")
    void gameplaySettings_updateEngineAndUiState_invalidZoneWidth() {
        int before = engine.snapshot().zoneWidth();

        assertThrows(IllegalArgumentException.class, () -> engine.setZoneWidth(0), "width=0 should throw");
        assertThrows(IllegalArgumentException.class, () -> engine.setZoneWidth(HitTheZoneEngine.WIDTH), "width=field width should throw");
        assertThrows(IllegalArgumentException.class, () -> engine.setZoneWidth(-1), "negative width should throw");

        assertEquals(before, engine.snapshot().zoneWidth(), "engine zone width should be unchanged after invalid input");
    }

    @Test
    @DisplayName("setBallSpeed() while paused still updates engine speed")
    void gameplaySettings_updateEngineAndUiState_ballSpeedWhilePaused() {
        engine.togglePause(); 
        engine.setBallSpeed(8);

        assertEquals(8, engine.snapshot().ballSpeed(), "ball speed should update even while paused");
    }

    @Test
    @DisplayName("setZoneWidth() while paused still updates engine zone width")
    void gameplaySettings_updateEngineAndUiState_zoneWidthWhilePaused() {
        engine.togglePause(); 
        engine.setZoneWidth(100);

        assertEquals(100, engine.snapshot().zoneWidth(), "zone width should update even while paused");
    }
}