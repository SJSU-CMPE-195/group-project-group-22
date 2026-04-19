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
    @DisplayName("EMA filter smooths successive readings (alpha=0.4)")
    void testEmaSmoothing() {
        // seeds the EMA directly (no smoothing)
        double v1 = parser.parseVoltage("0,0,0,4095"); // raw = 3.3V through EMA = 3.3
        assertEquals(3.3, v1, 0.001, "First reading should seed EMA directly");

        // raw = 0V goes through EMA = 0*0.4 + 3.3*0.6 = 1.98
        double v2 = parser.parseVoltage("0,0,0,0");
        assertEquals(1.98, v2, 0.01, "EMA should smooth toward 0V");
        assertTrue(v2 > 0.0 && v2 < 3.3, "Smoothed value should be between min and max");
    }

    // Channel selection

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

    // Filter state

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
}