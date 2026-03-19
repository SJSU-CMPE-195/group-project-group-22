package edu.sjsu.spring2026.group32.hardware;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Neural Signal Parser Suite")
class NeuralSignalParserTest {

    private final NeuralSignalParser parser = new NeuralSignalParser();

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
}