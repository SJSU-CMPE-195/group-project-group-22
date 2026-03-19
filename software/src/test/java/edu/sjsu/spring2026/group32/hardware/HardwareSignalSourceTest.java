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

        // Simulates connect() being called and failing to return false (used for simulating hardware not found)
        when(mockConnectionManager.connect()).thenReturn(false);

        // Simulates isConnected() being called (controls gate for getNextVoltage())
        when(mockConnectionManager.isConnected()).thenReturn(false);

        // Simulates no data coming from the hardware 
        when(mockConnectionManager.getNextLine()).thenReturn(null);

        // Instantiating triggers connect() 
        signalSource = new HardwareSignalSource(mockConnectionManager, realParser);
        double voltage = signalSource.getNextVoltage();

        // Uses verify() to ensure connect() was called exactly once
        assertEquals(0.0, voltage, 0.0001);
        verify(mockConnectionManager, times(1)).connect();

    }

    @Test
    @DisplayName("Should successfully connect, configure port, and read voltage")
    void testConnectionSuccessAndVoltageRead() {
        
        // Simulates successful connection (connect() returns true) 
        when(mockConnectionManager.connect()).thenReturn(true);
        when(mockConnectionManager.isConnected()).thenReturn(true);

        // Returns a valid fake string of data for getNextLine() (e.g., "123456,1,0,2047").
        when(mockConnectionManager.getNextLine()).thenReturn("123456,1,0,2047");

        // Instantiates the signalSource and calls getNextVoltage().
        signalSource = new HardwareSignalSource(mockConnectionManager, realParser);
        double voltage = signalSource.getNextVoltage();

        // Asserts the voltage correctly parses to ~1.649.
        assertEquals(1.649, voltage, 0.01);
        
        // Verifies that BOTH connect() and getNextLine() were called exactly 1 time on the mock.
        verify(mockConnectionManager, times(1)).connect();
        verify(mockConnectionManager, times(1)).getNextLine();


    }
}