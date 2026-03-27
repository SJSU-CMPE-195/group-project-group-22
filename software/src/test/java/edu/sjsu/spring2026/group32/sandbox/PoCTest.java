package edu.sjsu.spring2026.group32.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.sjsu.spring2026.group32.player.BasePlayer;

import static org.junit.jupiter.api.Assertions.*;

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
        // Simulates the ball reaching the right wall moving in the positive direction
        game.ballX = PoC_HitTheZone.WIDTH - PoC_HitTheZone.BALL_DIAM; 
        game.direction = PoC_HitTheZone.SPEED; 

        game.onTick();

        assertTrue(game.direction < 0, "Direction should be negative after hitting the right wall");
    }

    @Test
    @DisplayName("Test 2: Scoring should only be possible once per zone entry")
    void testScoreLockout() {
        // Simulates ball center on the zone boundary with scoring armed
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
        // Simulates game play (hits = 5, attempts = 10) before resetting
        game.hits[0] = 5; 
        game.attempts[0] = 10; 
        game.totalPasses = 3; 

        game.resetGame();

        // All counters should be 0 upon resetting
        assertEquals(0, game.hits[0], "successfullHits should be 0 after reset");
        assertEquals(0, game.attempts[0], "totalAttempts should be 0 after reset");
        assertEquals(0, game.totalPasses, "totalPasses should be 0 after reset");
        assertEquals(PoC_HitTheZone.START_X, game.ballX, "ballX should return to START_X after resetting");

    }

    @Test
    @DisplayName("Test 4: Pause should freeze movement")
    void testPauseLogic() {
        // Simulates pausing the game, records current ballX position onTick() should return immediately without moving the ball
        game.isPaused = true; 
        int originalX = game.ballX; 

        game.onTick();
        game.onTick();
        game.onTick();

        assertEquals(originalX, game.ballX, "Ball should not move while paused");

    }
}