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
        // TODO: Assert that an ADC value of 4095 results in exactly 3.3V.
        // TODO: Assert that an ADC value of 0 results in exactly 0.0V.
    }

    @Test
    @DisplayName("Should gracefully handle malformed or partial lines")
    void testMalformedData() {
        // TODO: Assert that passing an incomplete line (e.g., "123456,1,0") returns 0.0.
        // TODO: Assert that passing a non-numeric ADC (e.g., "123456,1,0,CORRUPTED") returns 0.0.
        // TODO: Assert that passing an empty string ("   ") returns 0.0.
        // TODO: Assert that passing 'null' returns 0.0.
    }
}