package edu.sjsu.spring2026.group32.hardware;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Hardware Connection Suite")
class HardwareSignalSourceTest {

    private HardwareSignalSource signalSource;
    private SerialConnectionManager mockConnectionManager;
    private NeuralSignalParser realParser;

    @BeforeEach
    void setUp() {
        mockConnectionManager = mock(SerialConnectionManager.class);
        realParser = new NeuralSignalParser();
    }

    @AfterEach
    void tearDown() {
        if (signalSource != null) {
            signalSource.close();
        }
    }

    @Test
    @DisplayName("Should handle missing hardware gracefully without crashing")
    void testMissingHardwareHandling() {

        // When connect() is called, pretend it fails and return false (used for simulating hardware not found)
        when(mockConnectionManager.connect()).thenReturn(false);

        // When isConnected() is called, return false (controls gate for getNextVoltage())
        when(mockConnectionManager.isConnected()).thenReturn(false);

        // Simulating no data coming from the hardware 
        when(mockConnectionManager.getNextLine()).thenReturn(null);

        // Instantiating triggers connect() internally 
        signalSource = new HardwareSignalSource(mockConnectionManager, realParser);
        double voltage = signalSource.getNextVoltage();

        // Assert, using Mockito's verify() to ensure connect() was called exactly 1 time
        assertEquals(0.0, voltage, 0.0001);
        verify(mockConnectionManager, times(1)).connect();

    }

    @Test
    @DisplayName("Should successfully connect, configure port, and read voltage")
    void testConnectionSuccessAndVoltageRead() {
        // TODO (Arrange): Simulate a successful connection (connect() returns true) 
        // and return a valid fake string of data for getNextLine() (e.g., "123456,1,0,2047").

        // TODO (Act): Instantiate the 'signalSource' and call getNextVoltage().

        // TODO (Assert): Assert that the voltage correctly parses to ~1.649.
        // Verify that BOTH connect() and getNextLine() were called exactly 1 time on the mock.
    }
}