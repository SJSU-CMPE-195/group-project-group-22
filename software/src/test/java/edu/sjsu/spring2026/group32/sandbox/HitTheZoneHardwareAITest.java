package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.hardware.BaseSignalSource;
import edu.sjsu.spring2026.group32.player.PlayerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HitTheZoneHardwareAI}.
 *
 * <p>{@link BaseSignalSource} is a single-abstract-method interface, so a
 * lambda is used as a test stub — no mocking framework required.
 */
class HitTheZoneHardwareAITest {

    private HitTheZoneHardwareAI player;

    /** Creates a signal source that always returns the given fixed voltage. */
    private static BaseSignalSource fixed(double voltage) {
        // Return a BaseSignalSource lambda whose getNextVoltage() always returns voltage.
        return () -> voltage;
    }

    @BeforeEach
    void setUp() {
        // Simulates a default player with the 1.0V voltage threshold and always-hot signal source 2.0 V
        player = new HitTheZoneHardwareAI("bot", fixed(2.0),1.0);
    }

    // ------------------------------------------------------------------
    // Basic action mapping
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Returns SCORE when in zone and voltage meets threshold")
    void scoreWhenInZoneAndVoltageHigh() {
        assertEquals(HitTheZoneAction.SCORE, player.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Returns null when in zone but voltage is below threshold")
    void noScoreWhenVoltageLow() {
        // Hardware with voltage fixed voltage 0.5 and threshold: 1.0 
        HitTheZoneHardwareAI lowPlayer = new HitTheZoneHardwareAI("Bot", fixed(0.5), 1.0);
        assertNull(lowPlayer.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Returns null when outside zone regardless of voltage")
    void noScoreWhenOutsideZone() {
    
        // Hardware with voltage fixed voltage 3.3 and threshold: 1.0 
        HitTheZoneHardwareAI hotPlayer = new HitTheZoneHardwareAI("Bot", fixed(3.3), 1.0);
        
        // Zone check must gate the voltage check, even max voltage should not score outside zone
        assertNull(hotPlayer.getNextMove(new HitTheZoneState(false)));

    }

    // ------------------------------------------------------------------
    // Threshold boundary
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Returns SCORE at exactly the threshold voltage (inclusive)")
    void scoreAtExactThreshold() {

        // Hardware with fixed voltage 1.0 and voltage threshold 1.0 
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(1.0), 1.0);
        assertEquals(HitTheZoneAction.SCORE, p.getNextMove(new HitTheZoneState(true)));

    }

    @Test
    @DisplayName("Returns null just below the threshold voltage")
    void noScoreJustBelowThreshold() {

        // Simulates hardware with fixed voltage 0.99 and voltage threshold 1.0 
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(0.99), 1.0);
        assertNull(p.getNextMove(new HitTheZoneState(true)));
    }

    @Test
    @DisplayName("Custom threshold is respected")
    void customThresholdRespected() {

        // Simulates hardware with fixed voltage = 2.0 and voltageThreshold = 2.5 
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Bot", fixed(2.0), 2.5);
        assertNull(p.getNextMove(new HitTheZoneState(true)));

    }

    // ------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getName() returns the name passed to the constructor")
    void getNameReturnsConstructorValue() {
        HitTheZoneHardwareAI p = new HitTheZoneHardwareAI("Neural", fixed(2.0), 1.0);
        assertEquals("Neural", p.getName());

    }

    @Test
    @DisplayName("getType() returns HARDWARE")
    void getTypeReturnsHardware() {
        assertEquals(PlayerType.HARDWARE, player.getType());

    }
}
