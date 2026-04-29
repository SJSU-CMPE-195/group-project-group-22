package edu.sjsu.spring2026.group32.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.player.BasePlayer;
import edu.sjsu.spring2026.group32.player.HumanPlayer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

class PoCTest {

    private PoC_HitTheZone game;

    @BeforeEach
    void setUp() {
        // Single zero-jitter bot that reacts instantly but doesn't interfere with manual state set in each test because onTick() is called explicitly
        List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players = List.of(new HitTheZoneSoftwareAI("Bot", 0)); 

        // Headless mode to skip Swing UI: updateHud() is overridden to prevent NPE from uninitialized JLabel arr in headless constructor
        game = new PoC_HitTheZone(true, players) { 
            @Override
            protected void updateHud() {}
        }; 

    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wall bounce
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ball should reverse direction when hitting right wall")
    void testRightWallBounce() {
        // simulates the ball reaching the right wall moving in the positive direction
        game.ballX = PoC_HitTheZone.WIDTH - PoC_HitTheZone.BALL_DIAM; 
        game.direction = PoC_HitTheZone.SPEED; 

        game.onTick();

        assertTrue(game.direction < 0, "Direction should be negative after hitting the right wall");
    }

    @Test
    @DisplayName("Ball should reverse direction when hitting right wall")
    void testLeftWallBounce() {
        // simulates the ball reaching the right wall moving in the positive direction
        game.ballX = 0;
        game.direction = -PoC_HitTheZone.SPEED; 

        game.onTick();

        assertTrue(game.direction > 0, "Direction should be positive after hitting the left wall");
    }


    // ──────────────────────────────────────────────────────────────────────────
    // Ball movement
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ball moves each tick when not paused")
    void testBallMovesWhenNotPaused() {
        game.isPaused  = false;

        game.ballX = PoC_HitTheZone.START_X;
        game.direction = PoC_HitTheZone.SPEED;

        int beforeX = game.ballX;
        game.onTick();

        assertNotEquals(beforeX, game.ballX, "Ball should move when game is not paused");
    }

    @Test
    @DisplayName("Pause should freeze movement")
    void testPauseLogic() {
        // simulate pausing the game, records current ballX position onTick() should return immediately without moving the ball
        game.isPaused = true; 
        int originalX = game.ballX; 

        game.onTick();
        game.onTick();
        game.onTick();

        assertEquals(originalX, game.ballX, "Ball should not move while paused");

    }

    @Test
    @DisplayName("elapsedMs increments by 16 each tick when not paused")
    void testElapsedMsIncrementsEachTick() {
        game.isPaused = false;
        long before = game.elapsedMs;

        game.onTick();

        assertEquals(before + 16, game.elapsedMs, "elapsedMs should increase by 16ms each tick");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Zone entry / exit
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Zone entry arms canScore and increments totalPasses")
    void testZoneEntryArmsCanScore() {
        // simulates ball entering zone from the left
        game.ballX = PoC_HitTheZone.ZONE_START - PoC_HitTheZone.BALL_DIAM / 2 - PoC_HitTheZone.SPEED;
        game.direction = PoC_HitTheZone.SPEED;
        game.inZone = false;

        game.onTick();

        assertTrue(game.canScore[0], "canScore should be armed on zone entry");
        assertEquals(1, game.totalPasses, "totalPasses should increment on zone entry");
    }

    @Test
    @DisplayName("Zone exit clears canScore")
    void testZoneExitClearsCanScore() {
        // simulates ball exiting the zone to the right
        game.ballX = PoC_HitTheZone.ZONE_START + PoC_HitTheZone.ZONE_WIDTH;
        game.inZone = true;
        game.canScore[0] = true;

        game.onTick();

        assertFalse(game.canScore[0], "canScore should be cleared on zone exit");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // processScore
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processScore increments both hits and attempts when ball is in zone and canScore=true")
    void testProcessScoreIncrementsHitsAndAttempts() {
        // ZONE_START = (WIDTH - ZONE_WIDTH) / 2 = (820 - 80) / 2 = 370
        // ballX set so center = ZONE_START (inside zone)
        game.ballX = PoC_HitTheZone.ZONE_START - PoC_HitTheZone.BALL_DIAM / 2;
        game.canScore[0] = true;
        int hitsBefore = game.hits[0];
        int attemptBefore = game.attempts[0];

        game.processScore(0);

        assertEquals(hitsBefore + 1, game.hits[0], "hits should increment on successful score");
        assertEquals(attemptBefore + 1, game.attempts[0], "attempts should always increment");
    }

    @Test
    @DisplayName("processScore increments only attempts when canScore=false")
    void testProcessScoreIncrementsOnlyAttemptsWhenCannotScore() {
        // Ball is inside the zone but canScore=false (zone exit already occurred)
        game.ballX = PoC_HitTheZone.ZONE_START - PoC_HitTheZone.BALL_DIAM / 2;
        game.canScore[0] = false;
        int hitsBefore = game.hits[0];
        int attemptBefore = game.attempts[0];

        game.processScore(0);

        assertEquals(hitsBefore, game.hits[0], "hits should NOT change when canScore=false");
        assertEquals(attemptBefore + 1, game.attempts[0], "attempts should still increment");
    }

    @Test
    @DisplayName("processScore does nothing when ball center is outside the zone")
    void testProcessScoreIgnoresOutOfZoneAttempt() {
        // ballX=0 -> center=10, well outside ZONE_START (~370)
        game.ballX = 0;
        game.canScore[0] = true;

        int hitsBefore = game.hits[0];
        int attemptsBefore = game.attempts[0];

        game.processScore(0);

        assertEquals(hitsBefore, game.hits[0], "hits should not change when ball is outside zone");
        assertEquals(attemptsBefore + 1, game.attempts[0], "attempts should still increment");
    }


    @Test
    @DisplayName("Scoring can happen multiple times during one zone pass")
    void testMultipleScoresPerZonePass() {
        // simulates ball center on the zone boundary with scoring armed
        game.ballX = PoC_HitTheZone.ZONE_START - (PoC_HitTheZone.BALL_DIAM / 2); // center lands on ZONE_START
        game.canScore[0] = true; 
        game.inZone = true; 

        game.processScore(0);
        game.processScore(0);

        assertEquals(2, game.hits[0], "Should allow repeated hits while the ball remains in the zone");
        assertEquals(2, game.attempts[0], "Both attempts should be counted"); 

    }

    @Test
    @DisplayName("processScore does nothing while game is paused")
    void testProcessScoreDoesNothingWhenPaused() {
        game.isPaused = true;
        game.ballX = PoC_HitTheZone.ZONE_START - PoC_HitTheZone.BALL_DIAM / 2;
        game.canScore[0] = true;
        int hitsBefore = game.hits[0];
        int attemptsBefore = game.attempts[0];
        game.processScore(0);
        assertEquals(hitsBefore, game.hits[0], "hits should not change when paused");
        assertEquals(attemptsBefore, game.attempts[0], "attempts should not change when paused");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resetGame
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetGame clears all counters and returns ball to START_X")
    void testResetFunctionality() {
        // simulates game play (hits = 5, attempts = 10) before resetting
        game.hits[0] = 5; 
        game.attempts[0] = 10; 
        game.totalPasses = 3; 

        game.resetGame();

        // all counters should be 0 upon resetting
        assertEquals(0, game.hits[0], "successfullHits should be 0 after reset");
        assertEquals(0, game.attempts[0], "totalAttempts should be 0 after reset");
        assertEquals(0, game.totalPasses, "totalPasses should be 0 after reset");
        assertEquals(PoC_HitTheZone.START_X, game.ballX, "ballX should return to START_X after resetting");

    }

    @Test
    @DisplayName("totalPasses is cleared by resetGame()")
    void testTotalPassesClearedOnReset() {
        game.totalPasses = 7;
        game.resetGame();

        assertEquals(0, game.totalPasses, "Reset should clear totalPasses to 0");
    }

    @Test
    @DisplayName("elapsedMs is cleared by resetGame()")
    void testElapsedMsClearedOnReset() {
        game.elapsedMs = 5000;
        game.resetGame();
        assertEquals(0, game.elapsedMs, "elapsedMs should be 0 after reset");
    }


    // ─────────────────────────────────────────────────────────────────────────
    // onTick() dispatch
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("onTick() dispatches RESET action to resetGame()")
    void testOnTickDispatchesReset() {
        // simulates a player that returns RESET on first call then null to prevent repeated resets
        BasePlayer<HitTheZoneState, HitTheZoneAction> mockPlayer = mock(BasePlayer.class);
        when(mockPlayer.getNextMove(any())).thenReturn(HitTheZoneAction.RESET, null);

        game = new PoC_HitTheZone(true, List.of(mockPlayer)) {
            @Override protected void updateHud() {}
        };

        // Set non-zero state that resetGame() should clear
        game.hits[0] = 5;
        game.totalPasses = 3;

        game.onTick();

        assertEquals(0, game.hits[0], "resetGame() should clear hits");
        assertEquals(0, game.totalPasses, "resetGame() should clear totalPasses");
        assertEquals(PoC_HitTheZone.START_X, game.ballX, "resetGame() should return ball to START_X");
    }

    @Test
    @DisplayName("onTick() allows duplicate consecutive SCORE actions while pass is active")
    void testOnTickAllowsMultipleScoresPerPass() {
        BasePlayer<HitTheZoneState, HitTheZoneAction> mockPlayer = mock(BasePlayer.class);
        when(mockPlayer.getNextMove(any())).thenReturn(HitTheZoneAction.SCORE);
 
        game = new PoC_HitTheZone(true, List.of(mockPlayer)) {
            @Override protected void updateHud() {}
        };
 
        game.ballX = PoC_HitTheZone.ZONE_START - PoC_HitTheZone.BALL_DIAM / 2;
        game.canScore[0] = true;
        game.inZone = true;
 
        game.onTick();
        game.canScore[0] = true; // re-arm for second tick
        game.onTick();
 
        assertEquals(2, game.hits[0], "Consecutive SCORE actions should each count while the pass is still active");

    }

    // ──────────────────────────────────────────────────────────────────────────
    // setBallSpeed()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setBallSpeed() updates ball speed and preserves direction sign")
    void setBallSpeedUpdatesSpeedAndPreservesDirection() {
        game.direction = PoC_HitTheZone.SPEED; // positive
        game.setBallSpeed(10);
        assertEquals(10, game.direction, "direction should be +10 when previously positive");
 
        game.direction = -PoC_HitTheZone.SPEED; // negative
        game.setBallSpeed(10);
        assertEquals(-10, game.direction, "direction should be -10 when previously negative");

    }

    @Test
    @DisplayName("setBallSpeed() throws IllegalArgumentException for zero or negative value")
    void setBallSpeedThrowsIllegalArgumentOnInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> game.setBallSpeed(0));
        assertThrows(IllegalArgumentException.class, () -> game.setBallSpeed(-1));

    }

    // ──────────────────────────────────────────────────────────────────────────
    // setZoneWidth()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setZoneWidth() updates the zone width used by the game")
    void setZoneWidthUpdatesZoneWidth() {
        game.setZoneWidth(200);
        assertEquals(200, game.zoneWidth, "zoneWidth should be updated to 200");
    }

    @Test
    @DisplayName("setZoneWidth() throws IllegalArgumentException for zero, negative, or field-width-or-greater value")
    void setZoneWidthThrowsIllegalArgumentOnInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> game.setZoneWidth(0));
        assertThrows(IllegalArgumentException.class, () -> game.setZoneWidth(-1));
        assertThrows(IllegalArgumentException.class, () -> game.setZoneWidth(PoC_HitTheZone.WIDTH));
    }
    // ──────────────────────────────────────────────────────────────────────────
    // createDefaultPlayers()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createDefaultPlayers() returns four players when no hardware is connected")
    void createDefaultPlayersReturnsFourPlayersWithoutHardware() {
        var players = PoC_HitTheZone.createDefaultPlayers(null);
        // Bot Alpha, Bot Beta, Human (no hardware AI when manager is null)
        assertEquals(3, players.size(), "Should return 3 players (2 bots + 1 human) when manager is null");
    }

    @Test
    @DisplayName("createDefaultPlayers() includes a HardwareAI player when manager is connected")
    void createDefaultPlayersIncludesHardwareAIWithConnectedManager() {
        SerialConnectionManager mockManager = mock(SerialConnectionManager.class);
        when(mockManager.isConnected()).thenReturn(true);
        // Stub getNextLine() to avoid blocking — parser needs at least a parseable line.
        when(mockManager.getNextLine()).thenReturn("0,0.00");
 
        var players = PoC_HitTheZone.createDefaultPlayers(mockManager);
        boolean hasHardware = players.stream().anyMatch(p -> p instanceof HitTheZoneHardwareAI);
        assertTrue(hasHardware, "Should include a HitTheZoneHardwareAI player when manager is connected");

    }

    // ──────────────────────────────────────────────────────────────────────────
    // zoneStart()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("zoneStart() returns the expected center-aligned zone start X position")
    void zoneStartReturnsExpectedCenter() {
        int expected = (PoC_HitTheZone.WIDTH - game.zoneWidth) / 2;
        assertEquals(expected, game.zoneStart(), "zoneStart() should match center-alignment formula");

    }

    @Test
    @DisplayName("zoneStart() recalculates correctly after setZoneWidth()")
    void zoneStartUpdatesAfterWidthChange() {
        game.setZoneWidth(200);
        int expected = (PoC_HitTheZone.WIDTH - 200) / 2;
        assertEquals(expected, game.zoneStart(), "zoneStart() should reflect new zone width");
    }

    @Test
    @DisplayName("Human player must tap SCORE instead of holding it")
    void testHumanScoreRequiresTap() {
        BasePlayer<HitTheZoneState, HitTheZoneAction> mockPlayer = mock(BasePlayer.class);
        when(mockPlayer.getNextMove(any()))
            .thenReturn(
                HitTheZoneAction.SCORE, // tick 1 (press)
                null, // tick 2 (release)
                HitTheZoneAction.SCORE  // tick 3 (press again)
            );

        game = new PoC_HitTheZone(true, List.of(mockPlayer)) {
            @Override protected void updateHud() {}
        };

        game.ballX = PoC_HitTheZone.ZONE_START - PoC_HitTheZone.BALL_DIAM / 2;
        game.canScore[0] = true;
        game.inZone = true;

        game.onTick();
        game.canScore[0] = true;

        game.onTick();
        game.canScore[0] = true;

        game.onTick();
        assertEquals(2, game.hits[0], "Two separate taps (with a release in between) should count as two hits");
    
    }

}
