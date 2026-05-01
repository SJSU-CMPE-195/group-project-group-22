package edu.sjsu.spring2026.group32.hardware;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Neural Signal Parser Suite")
class NeuralSignalParserTest {

    private NeuralSignalParser parser = new NeuralSignalParser();

    @BeforeEach
    void setUp() {
        // This needs to be re-instantiated before each test because NeuralSignalParser is stateful
        // A shared instance would cause the voltage history to leak between test cases
        parser = new NeuralSignalParser();
    }


    @Test
    @DisplayName("Should correctly parse a standard valid line")
    void testValidDataParsing() {
        // Passes the string "123456,1,0,2047" to the parser
        double voltage = parser.parseVoltage("123456,1,0,2047");

        // Asserts that the returned voltage is 1.649 (use a delta of 0.01 for double comparison)
        assertEquals(1.649, voltage, 0.01);

    }

    @Test
    @DisplayName("Should handle max and min ADC values bounds")
    void testAdcBounds() {
        // Asserts that an ADC value of 4095 results in exactly 3.3V (because (4095 / 4095) * 3.3 = 3.3 V) 
        assertEquals(3.3, parser.parseVoltage("123456,1,0,4095"),0.0001);

        // Asserts that an ADC value of 0 results in exactly 0.0V.
        // Min bound: used to reset so the 0-ADC read is also a first reading so its unaffected by a prior state
        parser.resetFilter();
        assertEquals(0.0, parser.parseVoltage("123456,1,0,0"),0.001);
    }

    @Test
    @DisplayName("Should gracefully handle malformed or partial lines")
    void testMalformedData() {
        // Asserts that passing an incomplete line (e.g., "123456,1,0") returns 0.0.
        assertEquals(0.0, parser.parseVoltage("123456,1,0"),0.0001);
    
        // Asserts that passing a non-numeric ADC (e.g., "123456,1,0,CORRUPTED") returns 0.0.
        assertEquals(0.0, parser.parseVoltage("123456,1,0,CORRUPTED"),0.0001);

        // Asserts that passing an empty string ("   ") returns 0.0.
        assertEquals(0.0, parser.parseVoltage("   "),0.0001);

        // Asserts that passing 'null' returns 0.0.
        assertEquals(0.0, parser.parseVoltage(null),0.0001);
    }


    // New tests

    @Test
    @DisplayName("Default parser (alpha=1.0) passes raw voltage through without smoothing")
    void testDefaultParserNoSmoothing() {
        double v1 = parser.parseVoltage("0,0,0,4095"); // raw = 3.3V
        assertEquals(3.3, v1, 0.001, "First reading should return raw voltage");

        // raw = 0V should pass through immediately (no EMA lag)
        double v2 = parser.parseVoltage("0,0,0,0");
        assertEquals(0.0, v2, 0.001, "With alpha=1.0, voltage should track raw value instantly");
    }

    @Test
    @DisplayName("EMA filter smooths successive readings when alpha < 1.0")
    void testEmaSmoothing() {
        // Explicit alpha=0.4 parser to verify EMA logic still works
        NeuralSignalParser emaParser = new NeuralSignalParser(4095.0, 3.3, 0.4, 0);

        double v1 = emaParser.parseVoltage("0,0,0,4095"); // raw = 3.3V, seeds EMA = 3.3
        assertEquals(3.3, v1, 0.001, "First reading should seed EMA directly");

        // raw = 0V goes through EMA = 0*0.4 + 3.3*0.6 = 1.98
        double v2 = emaParser.parseVoltage("0,0,0,0");
        assertEquals(1.98, v2, 0.01, "EMA should smooth toward 0V");
        assertTrue(v2 > 0.0 && v2 < 3.3, "Smoothed value should be between min and max");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Channel selection
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Channel index 1 reads parts[4] (GPIO35 dual-channel config)")
    void testChannelIndex1ReadsColumn4() {
        // simulates dual-channel Pong config where GPIO35 carries second signal
        // CSV: "ts,a,b,ch0,ch1", the channel 1 parser should read parts[4] 
        NeuralSignalParser ch1Parser = new NeuralSignalParser(1);

        // ch0 = 0 (0V), ch1 = 4095 (3.3V) should return 3.3V not 0V 
        double voltage = ch1Parser.parseVoltage("123456,1,0,0,4095");
        assertEquals(3.3, voltage, 0.001, "Channel 1 parser should read column 4 (parts[4])");
    }

    @Test
    @DisplayName("Channel index 0 ignores column 4 (reads parts[3] only)")
    void testChannelIndex0ReadsColumn3() {

        // ch0 = 2047 (1.649V), ch1 = 4095 (3.3V) channel 0 parser should return around 1.649V
        double voltage = parser.parseVoltage("123456,1,0,2047,4095");
        assertEquals(1.649, voltage, 0.01, "Channel 0 parser should read column 3 only");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Filter state
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetFilter() restores initial state so next reading seeds EMA fresh")
    void testResetFilterRestoresInitialState() {
        parser.parseVoltage("0,0,0,4095"); // seed EMA at 3.3V
        parser.resetFilter();
        double afterReset = parser.parseVoltage("0,0,0,0"); // should seed directly at 0V
        assertEquals(0.0, afterReset, 0.001, "After resetFilter(), first reading should seed EMA directly at 0V");
    }

    @Test
    @DisplayName("Full constructor: custom ADC range and system voltage are applied")
    void testFullConstructorCustomAdcAndVoltage() {
        // 10-bit Arduino ADC (0-1023), 5.0V reference, no smoothing (alpha=1.0), channel 0
        NeuralSignalParser arduinoParser = new NeuralSignalParser(1023.0, 5.0, 1.0, 0);
        double voltage = arduinoParser.parseVoltage("0,0,0,1023");
        assertEquals(5.0, voltage, 0.001, "Full-scale reading should equal systemVoltage");
    }

    @Test
    @DisplayName("Malformed line mid-stream returns last known EMA value")
    void testMalformedLineMidStreamReturnsLastEma() {
        parser.parseVoltage("0,0,0,2047");
        double lastGood = parser.parseVoltage("0,0,0,2047");

        double afterMalformed = parser.parseVoltage("0,0,0,BAD");
        assertEquals(lastGood, afterMalformed, 0.0001, "Malformed line should return last known EMA value unchanged");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // hasSpike() — rising-edge detection
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hasSpike() returns true only on the first above-threshold call (rising edge)")
    void hasSpikeReturnsTrueOnlyOnRisingEdge() {
        double threshold = 0.5;

        // Seed below threshold so lastWasAbove starts false
        parser.parseVoltage("0,0,0,0");          // 0 V — below
        assertFalse(parser.hasSpike(threshold),  "Below threshold: no spike");

        // Voltage crosses above threshold → rising edge → spike
        parser.parseVoltage("0,0,0,4095");       // 3.3 V — above
        assertTrue(parser.hasSpike(threshold),   "First above-threshold call: rising edge → spike");

        // Voltage stays high → NO second spike
        parser.parseVoltage("0,0,0,4095");       // still 3.3 V
        assertFalse(parser.hasSpike(threshold),  "Voltage held high: plateau suppressed");

        // Voltage returns to baseline → resets state
        parser.parseVoltage("0,0,0,0");          // 0 V — below
        assertFalse(parser.hasSpike(threshold),  "Falling edge: no spike");

        // New spike after baseline
        parser.parseVoltage("0,0,0,4095");       // 3.3 V again
        assertTrue(parser.hasSpike(threshold),   "Second crossing: new spike detected");
    }

    @Test
    @DisplayName("hasSpike() correctly counts discrete spikes separated by baseline")
    void hasSpikeCountsDiscreteEvents() {
        double threshold = 1.0;
        int spikeCount = 0;

        // Three spike-then-baseline cycles
        for (int i = 0; i < 3; i++) {
            parser.parseVoltage("0,0,0,0");      // baseline
            parser.hasSpike(threshold);           // ensure lastWasAbove = false

            parser.parseVoltage("0,0,0,4095");   // spike onset
            if (parser.hasSpike(threshold)) spikeCount++;

            // Held high for two more ticks — must NOT increment count
            parser.parseVoltage("0,0,0,4095");
            if (parser.hasSpike(threshold)) spikeCount++;

            parser.parseVoltage("0,0,0,4095");
            if (parser.hasSpike(threshold)) spikeCount++;
        }

        assertEquals(3, spikeCount,
                "Each low-to-high crossing counts as exactly one spike");
    }

    @Test
    @DisplayName("hasSpike() at exact threshold boundary fires on crossing, not below")
    void hasSpikeRespectsBoundary() {
        double threshold = 1.0;

        // Simulate just-below voltage (raw ≈ 1240 for 1.0 V threshold)
        // Use a custom parser for fine-grained control
        NeuralSignalParser p2 = new NeuralSignalParser(4095.0, 3.3, 1.0, 0);

        // Just below threshold (0.999 V ≈ raw 1240)
        p2.parseVoltage("0,0,0,1240");
        assertFalse(p2.hasSpike(threshold), "Just below 1.0 V: no spike");

        // At exact threshold (1.0 V ≈ raw 1241)
        p2.parseVoltage("0,0,0,1241");
        assertTrue(p2.hasSpike(threshold), "At or above threshold: spike fires");
    }

    @Test
    @DisplayName("resetFilter() clears lastWasAbove so next above-threshold call fires again")
    void resetFilterClearsSpikeLatchState() {
        double threshold = 0.5;

        parser.parseVoltage("0,0,0,4095");       // 3.3 V
        assertTrue(parser.hasSpike(threshold),   "First spike fires");

        parser.parseVoltage("0,0,0,4095");       // still high
        assertFalse(parser.hasSpike(threshold),  "Plateau suppressed");

        // Reset simulates hardware reconnect / game reset
        parser.resetFilter();

        // After reset, next above-threshold read is treated as a fresh rising edge
        parser.parseVoltage("0,0,0,4095");
        assertTrue(parser.hasSpike(threshold),
                "After resetFilter(), next above-threshold call fires as a new spike");
    }
}