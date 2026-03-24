package edu.sjsu.spring2026.group32.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoCTest {

    private PoC_HitTheZone game;

    @BeforeEach
    void setUp() {
        // TODO: Create a player list with a single zero-jitter software bot.
        // Zero jitter means the bot reacts instantly but never interferes
        // with the manual game state set in each test.
        // Use the headless constructor (true) to skip Swing GUI initialisation.


        // We initialize the GUI, but we will test the underlying logic variables
        game = new PoC_HitTheZone(true);
    }

    @Test
    @DisplayName("Test 1: Ball should reverse direction when hitting right wall")
    void testRightWallBounce() {
        // Set ball to the far right (Width - Diameter)
        game.ballX = PoC_HitTheZone.WIDTH - PoC_HitTheZone.BALL_DIAMETER; 
        game.direction = PoC_HitTheZone.SPEED; 

        // Call the onTick() method
        game.onTick();

        // Assert Check if 'direction' is now negative
        assertTrue(game.direction < 0, "Direction should be negative after hitting the right wall");
        
    }

    @Test
    @DisplayName("Test 2: Scoring should only be possible once per zone entry")
    void testScoreLockout() {
        // TODO: Refactor game.canScore, change attemptScore to processScore(),
        //  refactor successfullHits + totalAttempts, assert  hits[0] == 1 and attempts[0] == 2 

        // Move ball into the zone
        game.ballX = PoC_HitTheZone.ZONE_START - (PoC_HitTheZone.BALL_DIAMETER / 2); // center lands on ZONE_START
        game.canScore = true; 
        game.inZone = true; 

        // Call attemptScore() twice 
        game.attemptScore(); 
        game.attemptScore();

        // Assert successfulHits should be 1, not 2
        assertEquals(1, game.successfulHits, "Should only score once per zone entry");
        assertEquals(2, game.totalAttempts, "Both attempts should be counted"); 

    }

    @Test
    @DisplayName("Test 3: Reset should clear all counters")
    void testResetFunctionality() {
        // TODO: Set hits[0], attempts[0], and totalPasses to non-zero values.
        // Assert hits[0], attempts[0], and totalPasses are all 0.

        // Simulate some game play (hits = 5, attempts = 10) 
        game.successfulHits = 5; 
        game.totalAttempts = 10; 
        game.totalPasses = 3; 

        // Call resetGame()
        game.resetGame();

        // All counters should be 0 
        assertEquals(0, game.successfulHits, "successfullHits should be 0 after reset");
        assertEquals(0, game.totalAttempts, "totalAttempts should be 0 after reset");
        assertEquals(0, game.totalPasses, "totalPasses should be 0 after reset");
        assertEquals(PoC_HitTheZone.START_X, game.ballX, "ballX should return to START_X after resetting");

    }

    @Test
    @DisplayName("Test 4: Pause should freeze movement")
    void testPauseLogic() {
        // Set isPaused to true + record current ballX position
        game.isPaused = true; 
        int originalX = game.ballX; 

        // Call onTick() multiple times
        game.onTick();
        game.onTick();
        game.onTick();

        // Ball should not have moved
        assertEquals(originalX, game.ballX, "Ball should not move while paused");

    }
}