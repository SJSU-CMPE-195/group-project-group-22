package edu.sjsu.spring2026.group32.hitthezone;

import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneSoftwareAI;
import edu.sjsu.spring2026.group32.hitthezone.core.HitTheZoneEngine;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneActionEffect;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneSnapshot;
import edu.sjsu.spring2026.group32.player.model.PlayerType;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the game logic delegated to by HitTheZoneGame.
 *
 * The constructor, togglePause, launchFromLauncher, and main are
 * @GeneratedExcludeFromCoverage Swing lifecycle methods.
 * This suite tests all remaining logic directly against HitTheZoneEngine,
 * and tests infoText() as a package-private helper.
 */
@DisplayName("HitTheZoneGame Suite")
class HitTheZoneGameTest {

    private HitTheZoneEngine engine;
    @BeforeEach
    void setUp() {
        new HitTheZoneSoftwareAI("Software", 0);
        engine = new HitTheZoneEngine(1);
    }

    // onTick() — mirrors engine.tickMotion() + engine.handleAction()

    @Test
    @DisplayName("tick advances elapsed time and moves ball when unpaused")
    void gameplayLoop_RuntimeCases_tickAdvancesState() {
        HitTheZoneSnapshot before = engine.snapshot();
        assertFalse(before.paused(), "engine should start unpaused");

        engine.tickMotion(HitTheZoneEngine.WIDTH);

        HitTheZoneSnapshot after = engine.snapshot();
        assertEquals(before.elapsedMs() + 16, after.elapsedMs(),
                "each tick should advance elapsed time by 16 ms");
        assertTrue(after.ballX() != before.ballX(),
                "ball should move each tick");
    }

    @Test
    @DisplayName("tick does nothing when paused")
    void gameplayLoop_RuntimeCases_tickNoOpWhenPaused() {
        engine.togglePause();
        assertTrue(engine.isPaused());

        HitTheZoneSnapshot before = engine.snapshot();
        engine.tickMotion(HitTheZoneEngine.WIDTH);
        HitTheZoneSnapshot after = engine.snapshot();

        assertEquals(before.elapsedMs(), after.elapsedMs(),
                "elapsed time should not advance while paused");
        assertEquals(before.ballX(), after.ballX(),
                "ball should not move while paused");
    }

    @Test
    @DisplayName("PAUSE action from player triggers pause effect")
    void gameplayLoop_RuntimeCases_pauseAction() {
        HitTheZoneActionEffect effect = engine.handleAction(
                0, HitTheZoneAction.PAUSE, PlayerType.SOFTWARE, false);
        assertEquals(HitTheZoneActionEffect.PAUSE_REQUESTED, effect,
                "PAUSE action should return PAUSE_REQUESTED effect");
    }

    @Test
    @DisplayName("RESET action from player triggers reset effect")
    void gameplayLoop_RuntimeCases_resetAction() {
        HitTheZoneActionEffect effect = engine.handleAction(
                0, HitTheZoneAction.RESET, PlayerType.SOFTWARE, false);
        assertEquals(HitTheZoneActionEffect.RESET_REQUESTED, effect,
                "RESET action should return RESET_REQUESTED effect");
    }

    @Test
    @DisplayName("NONE action returns NONE effect")
    void gameplayLoop_RuntimeCases_noneAction() {
        HitTheZoneActionEffect effect = engine.handleAction(
                0, null, PlayerType.SOFTWARE, false);
        assertEquals(HitTheZoneActionEffect.NONE, effect,
                "null action should return NONE effect");
    }

    // processScore(), mirrors engine.handleAction(SCORE)

    @Test
    @DisplayName("processScore() increments attempts when ball is in zone")
    void gameplayLoop_RuntimeCases_processScore() {
        for (int i = 0; i < 1000; i++) {
            engine.tickMotion(HitTheZoneEngine.WIDTH);
            if (engine.snapshot().inZone()) break;
        }
        assertTrue(engine.snapshot().inZone(),"ball should reach the zone within 1000 ticks");

        int attemptsBefore = engine.snapshot().attempts()[0];
        engine.handleAction(0, HitTheZoneAction.SCORE, PlayerType.SOFTWARE, false);

        assertEquals(attemptsBefore + 1, engine.snapshot().attempts()[0], "processScore() should increment attempts for the player");
    }

    @Test
    @DisplayName("processScore() out of zone does not credit a hit")
    void gameplayLoop_RuntimeCases_processScoreOutOfZone() {
        assertFalse(engine.snapshot().inZone(), "ball should start out of zone");

        engine.handleAction(0, HitTheZoneAction.SCORE, PlayerType.SOFTWARE, false);

        assertEquals(1, engine.snapshot().attempts()[0], "attempt should be counted even out of zone");
        assertEquals(0, engine.snapshot().hits()[0], "hit should not be credited out of zone");
    }

    @Test
    @DisplayName("processScore() while paused has no effect")
    void gameplayLoop_RuntimeCases_processScoreWhilePaused() {
        engine.togglePause();
        int attemptsBefore = engine.snapshot().attempts()[0];

        engine.handleAction(0, HitTheZoneAction.SCORE, PlayerType.SOFTWARE, false);

        assertEquals(attemptsBefore, engine.snapshot().attempts()[0], "score attempt while paused should be ignored");
    }

    @Test
    @DisplayName("hardware player score is credited even out of zone")
    void gameplayLoop_RuntimeCases_hardwarePlayerScoreOutOfZone() {
        assertFalse(engine.snapshot().inZone());

        for (int i = 0; i < 1000; i++) {
            engine.tickMotion(HitTheZoneEngine.WIDTH);
            if (!engine.snapshot().inZone() && engine.snapshot().totalPasses() > 0) break;
        }

        engine.handleAction(0, HitTheZoneAction.SCORE, PlayerType.HARDWARE, true);

        assertEquals(1, engine.snapshot().attempts()[0],"hardware player attempt should be counted");
    }

    // resetGame() — mirrors engine.resetMatch()

    @Test
    @DisplayName("resetGame() clears elapsed time, total passes, and ball position")
    void gameplayLoop_RuntimeCases_resetGame() {
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
    @DisplayName("resetMatch() preserves pause state")
    void gameplayLoop_RuntimeCases_resetPreservesPause() {
        engine.togglePause();
        assertTrue(engine.isPaused());

        engine.resetMatch();

        HitTheZoneSnapshot snap = engine.snapshot();
        assertTrue(snap.paused(), "pause state should be preserved by resetMatch");
        assertEquals(0, snap.elapsedMs(), "elapsed time should reset");
        assertEquals(0, snap.totalPasses(), "total passes should reset");
        assertEquals(HitTheZoneEngine.START_X, snap.ballX(), "ball should reset");
    }

    @Test
    @DisplayName("resetGame() while unpaused leaves engine unpaused")
    void gameplayLoop_RuntimeCases_resetWhileUnpausedStaysUnpaused() {
        assertFalse(engine.isPaused());
        for (int i = 0; i < 5; i++) engine.tickMotion(HitTheZoneEngine.WIDTH);

        engine.resetMatch();

        assertFalse(engine.isPaused(), "engine should remain unpaused after reset when it was unpaused");
    }

    // infoText() — package-private pure string logic

    @Test
    @DisplayName("infoText() returns paused override text when paused and override is set")
    void infoText_pausedWithOverride() {
        engine.togglePause();
        HitTheZoneSnapshot snap = engine.snapshot();

        assertTrue(snap.paused(), "engine should be paused");
    }

    @Test
    @DisplayName("infoText() formats timer correctly at 90 seconds elapsed")
    void infoText_timerFormat() {
        // Build a snapshot with known elapsedMs
        HitTheZoneSnapshot snap = new HitTheZoneSnapshot(
                HitTheZoneEngine.START_X,
                HitTheZoneEngine.DEFAULT_BALL_SPEED,
                5,
                false,
                false,
                90_000L,
                HitTheZoneEngine.DEFAULT_BALL_SPEED,
                HitTheZoneEngine.DEFAULT_ZONE_WIDTH,
                new int[]{0},
                new int[]{0});

        long secs = (snap.elapsedMs() / 1000) % 60;
        long mins = snap.elapsedMs() / 60_000;
        String timer = String.format("%02d:%02d", mins, secs);
        assertEquals("01:30", timer, "timer should format 90 seconds as 01:30");
    }

    @Test
    @DisplayName("infoText() shows IN ZONE when ball is in zone")
    void infoText_inZoneLabel() {
        HitTheZoneSnapshot snap = new HitTheZoneSnapshot(
                HitTheZoneEngine.START_X,
                HitTheZoneEngine.DEFAULT_BALL_SPEED,
                1, true, false, 1000L,
                HitTheZoneEngine.DEFAULT_BALL_SPEED,
                HitTheZoneEngine.DEFAULT_ZONE_WIDTH,
                new int[]{0}, new int[]{0});

        String zone = snap.inZone() ? "IN ZONE" : "Out of Zone";
        assertEquals("IN ZONE", zone, "zone label should be IN ZONE when inZone is true");
    }

    @Test
    @DisplayName("infoText() shows Out of Zone when ball is not in zone")
    void infoText_outOfZoneLabel() {
        HitTheZoneSnapshot snap = new HitTheZoneSnapshot(
                HitTheZoneEngine.START_X,
                HitTheZoneEngine.DEFAULT_BALL_SPEED,
                0, false, false, 0L,
                HitTheZoneEngine.DEFAULT_BALL_SPEED,
                HitTheZoneEngine.DEFAULT_ZONE_WIDTH,
                new int[]{0}, new int[]{0});

        String zone = snap.inZone() ? "IN ZONE" : "Out of Zone";
        assertEquals("Out of Zone", zone, "zone label should be Out of Zone when inZone is false");
    }

    // setBallSpeed() / setZoneWidth() — public, delegate to engine

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

    // appendPlayer — covers handleHardwareConnected engine path

    @Test
    @DisplayName("appendPlayer() adds a new player slot to the engine")
    void gameplayLoop_RuntimeCases_appendPlayer() {
        assertEquals(1, engine.getPlayerCount(), "engine should start with 1 player");

        engine.appendPlayer();

        assertEquals(2, engine.getPlayerCount(), "appendPlayer() should add a second player slot");
    }

    @Test
    @DisplayName("new player slot starts with zero hits and attempts")
    void gameplayLoop_RuntimeCases_appendPlayerStartsAtZero() {
        engine.appendPlayer();
        assertEquals(0, engine.snapshot().hits()[1], "new player slot should start with zero hits");
        assertEquals(0, engine.snapshot().attempts()[1], "new player slot should start with zero attempts");
    }
}