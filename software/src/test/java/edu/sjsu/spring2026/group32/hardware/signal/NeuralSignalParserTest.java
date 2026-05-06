package edu.sjsu.spring2026.group32.hardware.signal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


@DisplayName("NeuralSignalParser Suite")
class NeuralSignalParserTest {

    private NeuralSignalParser parser;

    @BeforeEach
    void setUp() {
        parser = new NeuralSignalParser();
    }

    @Test
    @DisplayName("parseVoltage() handles null, blank, malformed, and valid single/dual-channel lines")
    void parseVoltage_handlesExpectedInputs() {
        assertEquals(0.0, parser.parseVoltage(null), 0.0001, "null input should return last known EMA (0.0 on first call)");

        assertEquals(0.0, parser.parseVoltage("    "), 0.0001, "blank input should return last known EMA");

        assertEquals(0.0, parser.parseVoltage("123456,0,0"), 0.0001, "line with fewer than 4 columns should return last known EMA");

        assertEquals(0.0, parser.parseVoltage("123456,0,0,GARBAGE"), 0.0001,"malformed ADC value should return last known EMA");

        assertEquals(0.0, parser.parseVoltage("1,0,0,0"), 0.0001, "ADC 0 → 0.0V");
        assertEquals(1.649, parser.parseVoltage("2,0,0,2047"), 0.01, "ADC 2047 → ~1.649V");
        assertEquals(3.3, parser.parseVoltage("3,0,0,4095"), 0.001, "ADC 4095 → 3.3V");

        NeuralSignalParser ch1 = new NeuralSignalParser(1);
        assertEquals(3.3, ch1.parseVoltage("1,0,0,0,4095"), 0.001, "channel 1 parser should read parts[4]");
        assertEquals(0.0, parser.parseVoltage("1,0,0,0,4095"), 0.0001, "channel 0 parser should ignore parts[4]");

        parser.parseVoltage("4,0,0,4095");
        assertEquals(3.3, parser.parseVoltage("5,0,0,GARBAGE"), 0.001, "malformed line after valid read should return last EMA, not 0.0");
    }

    @Test
    @DisplayName("parseVoltage() seeds EMA directly on first reading and smooths on subsequent readings")
    void parseVoltage_appliesSmoothingAndSeedsEma() {
        NeuralSignalParser smoothed = new NeuralSignalParser(4095.0, 3.3, 0.5, 0);

        assertEquals(3.3, smoothed.parseVoltage("1,0,0,4095"), 0.001, "first reading should seed EMA directly to raw voltage");

        // raw=0.0V: EMA = 0.5*0.0 + 0.5*3.3 = 1.65V
        assertEquals(1.65, smoothed.parseVoltage("2,0,0,0"), 0.001, "second reading should apply EMA: 0.5*0.0 + 0.5*3.3 = 1.65V");

        // raw=0.0V: EMA = 0.5*0.0 + 0.5*1.65 = 0.825V
        assertEquals(0.825, smoothed.parseVoltage("3,0,0,0"), 0.001, "third reading should continue smoothing toward 0.0V");

        // alpha=1.0 (default) is raw passthrough — no smoothing
        assertEquals(3.3, parser.parseVoltage("1,0,0,4095"), 0.001);
        assertEquals(0.0, parser.parseVoltage("2,0,0,0"), 0.0001);
        assertEquals(1.649, parser.parseVoltage("3,0,0,2047"), 0.01);
    }

    @Test
    @DisplayName("hasSpike() fires once per rising-edge crossing, resetFilter() clears EMA and re-arms")
    void hasSpike_detectsRisingEdgesAndResetClearsState() {
        // simulates  no spike
        parser.parseVoltage("1,0,0,0"); // 0.0V
        assertFalse(parser.hasSpike(1.0), "no spike below threshold");

        // simulates fires exactly once
        parser.parseVoltage("2,0,0,3000"); // ~2.42V
        assertTrue(parser.hasSpike(1.0), "first call above threshold should fire");
        assertFalse(parser.hasSpike(1.0), "sustained high should not re-fire");
        assertFalse(parser.hasSpike(1.0), "still sustained — should remain false");

        // simulates drop below then cross again 
        parser.parseVoltage("3,0,0,0"); // 0.0V
        assertFalse(parser.hasSpike(1.0), "no spike while below threshold");
        parser.parseVoltage("4,0,0,3000");
        assertTrue(parser.hasSpike(1.0), "second crossing should fire after re-arming");


        // simulates exactly at threshold counts as above (>=)
        parser.parseVoltage("5,0,0,0"); // drop below to re-arm
        assertFalse(parser.hasSpike(1.0), "re-arm: confirm below threshold");
        parser.parseVoltage("6,0,0,1241"); 

        assertTrue(parser.hasSpike(1.0), "voltage exactly at threshold should fire (>=)");
        assertFalse(parser.hasSpike(1.0), "should not re-fire at same threshold");

        parser.resetFilter();

        assertEquals(0.0, parser.parseVoltage(null), 0.0001, "EMA should be 0.0 after reset");

        parser.parseVoltage("7,0,0,3000");

        assertTrue(parser.hasSpike(1.0),"should detect rising edge again after reset clears lastWasAbove");
    }
}