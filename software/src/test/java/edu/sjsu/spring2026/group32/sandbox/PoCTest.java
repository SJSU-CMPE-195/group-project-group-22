package edu.sjsu.spring2026.group32.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.sjsu.spring2026.group32.player.BasePlayer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

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

    @Test
    @DisplayName("Test 1: Ball should reverse direction when hitting right wall")
    void testRightWallBounce() {
        // simulates the ball reaching the right wall moving in the positive direction
        game.ballX = PoC_HitTheZone.WIDTH - PoC_HitTheZone.BALL_DIAM; 
        game.direction = PoC_HitTheZone.SPEED; 

        game.onTick();

        assertTrue(game.direction < 0, "Direction should be negative after hitting the right wall");
    }

    @Test
    @DisplayName("Test 2: Scoring should only be possible once per zone entry")
    void testScoreLockout() {
        // simulates ball center on the zone boundary with scoring armed
        game.ballX = PoC_HitTheZone.ZONE_START - (PoC_HitTheZone.BALL_DIAM / 2); // center lands on ZONE_START
        game.canScore[0] = true; 
        game.inZone = true; 

        game.processScore(0);
        game.canScore[0] = false; // simulates zone exit where moveBall() clears canScore on zone exit
        game.processScore(0);

        assertEquals(1, game.hits[0], "Should only score once per zone entry");
        assertEquals(2, game.attempts[0], "Both attempts should be counted"); 

    }

    @Test
    @DisplayName("Test 3: Reset should clear all counters")
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
    @DisplayName("Test 4: Pause should freeze movement")
    void testPauseLogic() {
        // simulate pausing the game, records current ballX position onTick() should return immediately without moving the ball
        game.isPaused = true; 
        int originalX = game.ballX; 

        game.onTick();
        game.onTick();
        game.onTick();

        assertEquals(originalX, game.ballX, "Ball should not move while paused");

    }

    // ── New tests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 5: Ball should reverse direction when hitting left wall")
    void testLeftWallBounce() {
        game.ballX = 0;
        game.direction = -PoC_HitTheZone.SPEED;

        game.onTick();

        assertTrue(game.direction > 0, "Direction should be positive after hitting the left wall");
    }

    @Test
    @DisplayName("Test 6: Ball moves each tick when not paused")
    void testBallMovesWhenNotPaused() {
        game.isPaused  = false;
        game.ballX = PoC_HitTheZone.START_X;
        game.direction = PoC_HitTheZone.SPEED;

        int beforeX = game.ballX;
        game.onTick();

        assertNotEquals(beforeX, game.ballX, "Ball should move when game is not paused");
    }

    @Test
    @DisplayName("Test 7: processScore does nothing when ball center is outside the zone")
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
    @DisplayName("Test 8: totalPasses is cleared by resetGame()")
    void testTotalPassesClearedOnReset() {
        game.totalPasses = 7;
        game.resetGame();

        assertEquals(0, game.totalPasses, "Reset should clear totalPasses to 0");
    }

    @Test
    @DisplayName("Test 9: processScore increments both hits and attempts when ball is in zone and canScore=true")
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
    @DisplayName("Test 10: processScore increments only attempts when canScore=false")
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
    @DisplayName("Test 11: Zone entry arms canScore and increments totalPasses")
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
    @DisplayName("Test 12: Zone exit clears canScore")
    void testZoneExitClearsCanScore() {
        // simulates ball exiting the zone to the right
        game.ballX = PoC_HitTheZone.ZONE_START + PoC_HitTheZone.ZONE_WIDTH;
        game.inZone = true;
        game.canScore[0] = true;

        game.onTick();

        assertFalse(game.canScore[0], "canScore should be cleared on zone exit");
    }

    @Test
    @DisplayName("Test 13: elapsedMs increments by 16 each tick when not paused")
    void testElapsedMsIncrementsEachTick() {
        game.isPaused = false;
        long before = game.elapsedMs;

        game.onTick();

        assertEquals(before + 16, game.elapsedMs, "elapsedMs should increase by 16ms each tick");
    }

    @Test
    @DisplayName("Test 14: onTick() dispatches RESET action to resetGame()")
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
    @DisplayName("Test 15: onTick() edge detection blocks duplicate consecutive actions")
    void testOnTickEdgeDetection() {
        // simulates a player that always returns SCORE — edge detection should only process it on the first tick, blocking the second identical action
        BasePlayer<HitTheZoneState, HitTheZoneAction> mockPlayer = mock(BasePlayer.class);
        when(mockPlayer.getNextMove(any())).thenReturn(HitTheZoneAction.SCORE);

        game = new PoC_HitTheZone(true, List.of(mockPlayer)) {
            @Override protected void updateHud() {}
        };

        // place ball in zone with scoring armed
        game.ballX = PoC_HitTheZone.ZONE_START - PoC_HitTheZone.BALL_DIAM / 2;
        game.canScore[0] = true;
        game.inZone = true;

        game.onTick(); 
        game.canScore[0] = true;
        game.onTick();

        assertEquals(1, game.hits[0], "Edge detection should only process SCORE once even if returned on consecutive ticks");
    }


}