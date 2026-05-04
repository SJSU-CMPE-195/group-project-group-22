package edu.sjsu.spring2026.group32.hitthezone.core;

import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneActionEffect;
import edu.sjsu.spring2026.group32.player.model.PlayerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;

@DisplayName("HitTheZoneEngine Suite")
class HitTheZoneEngineTest {
    private HitTheZoneEngine engine;
    private final int INITIAL_PLAYERS = 1;

    @BeforeEach
    void setUp() {
        engine = new HitTheZoneEngine(INITIAL_PLAYERS);
    }

    @Test
    @DisplayName("lifecycleTransitions: verifies pause, resume, append-player, and reset")
    void lifecycleTransitions_coverPositiveAndNegativeCases() {

        assertFalse(engine.isPaused());
        engine.togglePause();

        assertTrue(engine.isPaused());
        engine.togglePause();

        assertFalse(engine.isPaused());

        assertEquals(1, engine.getPlayerCount());
        engine.appendPlayer();
        assertEquals(2, engine.getPlayerCount());

        engine.setBallSpeed(10);
        engine.resetMatch();

        var snapshot = engine.snapshot();

        assertEquals(HitTheZoneEngine.START_X, snapshot.ballX());
        assertEquals(0, snapshot.totalPasses());
        assertFalse(snapshot.paused());
    }

    @Test
    @DisplayName("ballMotionAndZoneTracking: verifies movement and zone entry/exit")
    void ballMotionAndZoneTracking_behaveAsExpected() {
        int iterations = 0;

        while (!engine.snapshot().inZone() && iterations < 100) {
            engine.tickMotion(HitTheZoneEngine.WIDTH);
            iterations++;
        }

        assertTrue(engine.snapshot().inZone(), "Ball should enter the zone");
        assertEquals(1, engine.snapshot().totalPasses(), "Total passes should increment on entry");

        // simulates moving ball out of the zone
        while (engine.snapshot().inZone() && iterations < 200) {
            engine.tickMotion(HitTheZoneEngine.WIDTH);
            iterations++;
        }
        assertFalse(engine.snapshot().inZone(), "Ball should exit the zone");
    }

    @Test
    @DisplayName("actionHandling: covers SCORE, PAUSE, and RESET logic")
    void actionHandling_coversHumanAndHardwarePositiveAndNegativePaths() {

        // simulates pause req
        var effect = engine.handleAction(0, HitTheZoneAction.PAUSE, PlayerType.HUMAN, false);
        assertEquals(HitTheZoneActionEffect.PAUSE_REQUESTED, effect);

        // simulates reset req
        effect = engine.handleAction(0, HitTheZoneAction.RESET, PlayerType.HUMAN, false);
        assertEquals(HitTheZoneActionEffect.RESET_REQUESTED, effect);

        // simulates scoring (Human needs to be in zone) --> move to zone 
        while(!engine.snapshot().inZone()) engine.tickMotion(HitTheZoneEngine.WIDTH);
        
        // simulates successful hit
        engine.handleAction(0, HitTheZoneAction.SCORE, PlayerType.HUMAN, false);
        assertEquals(1, engine.snapshot().hits()[0]);
        assertEquals(1, engine.snapshot().attempts()[0]);

        engine.handleAction(0, HitTheZoneAction.SCORE, PlayerType.HUMAN, false);
        assertEquals(1, engine.snapshot().hits()[0], "Should not credit hit for repeated action");
        
        engine.handleAction(0, HitTheZoneAction.SCORE, PlayerType.HARDWARE, true);
        assertEquals(2, engine.snapshot().attempts()[0]);
    }

    @Test
    @DisplayName("configurationUpdates: accepts valid and rejects invalid inputs")
    void configurationUpdates_acceptValidValuesAndRejectInvalidOnes() {
        // valid ball speed
        assertDoesNotThrow(() -> engine.setBallSpeed(10));
        
        // invalid ball speed
        assertThrows(IllegalArgumentException.class, () -> engine.setBallSpeed(0));
        assertThrows(IllegalArgumentException.class, () -> engine.setBallSpeed(-5));

        // valid zone width
        assertDoesNotThrow(() -> engine.setZoneWidth(100));

        // invalid zone width
        assertThrows(IllegalArgumentException.class, () -> engine.setZoneWidth(0));
        assertThrows(IllegalArgumentException.class, () -> engine.setZoneWidth(HitTheZoneEngine.WIDTH + 10));
    }
}