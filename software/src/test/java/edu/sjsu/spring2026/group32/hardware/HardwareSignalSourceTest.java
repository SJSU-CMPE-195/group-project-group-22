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

        // Uses verify() to ensure connect() was called exactly twice
        assertEquals(0.0, voltage, 0.0001);
        verify(mockConnectionManager, times(2)).connect();

    }

    @Test
    @DisplayName("Should successfully connect, configure port, and read voltage")
    void testConnectionSuccessAndVoltageRead() {
        
        // Simulates successful connection (connect() returns true) 
        when(mockConnectionManager.connect()).thenReturn(true);
        // First call (constructor guard): not yet connected → triggers connect().
        // Subsequent calls (getNextVoltage guard): connected → proceed to read.
        when(mockConnectionManager.isConnected()).thenReturn(false).thenReturn(true);

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

    // New tests 

    @Test
    @DisplayName("Should skip auto-connect when port is already open")
    void testSkipsAutoConnectWhenAlreadyConnected() {
        // isConnected() returns true constructor guard skips connect()
        when(mockConnectionManager.isConnected()).thenReturn(true);

        signalSource = new HardwareSignalSource(mockConnectionManager, realParser);

        verify(mockConnectionManager, never()).connect();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Voltage reading
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getNextVoltage() returns 0.0 when getNextLine() returns null")
    void testNullLineReturnsZero() {
        when(mockConnectionManager.isConnected()).thenReturn(false).thenReturn(true);
        when(mockConnectionManager.connect()).thenReturn(true);
        when(mockConnectionManager.getNextLine()).thenReturn(null);

        signalSource = new HardwareSignalSource(mockConnectionManager, realParser);
        double voltage = signalSource.getNextVoltage();

        assertEquals(0.0, voltage, 0.0001, "Null line from serial should produce 0.0V safe default");
    }

    @Test
    @DisplayName("getNextVoltage() triggers disconnect and returns 0.0 on read exception")
    void testExceptionDuringReadTriggersDisconnect() {
        when(mockConnectionManager.connect()).thenReturn(true);
        when(mockConnectionManager.isConnected()).thenReturn(false).thenReturn(true);
        when(mockConnectionManager.getNextLine()).thenThrow(new RuntimeException("port closed"));

        signalSource = new HardwareSignalSource(mockConnectionManager, realParser);
        double voltage = signalSource.getNextVoltage();

        assertEquals(0.0, voltage, 0.0001, "Exception during read should return 0.0V safe default");
        verify(mockConnectionManager).disconnect();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Reconnection
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Reconnect attempt is throttled by 2-second cooldown")
    void testReconnectCooldownThrottlesAttempts() {
        // isConnected() calls return false so handleDisconnection() is triggered
        when(mockConnectionManager.isConnected()).thenReturn(false);
        when(mockConnectionManager.connect()).thenReturn(false);

        signalSource = new HardwareSignalSource(mockConnectionManager, realParser);

        // triggers reconnect attempt
        signalSource.getNextVoltage();
        // call again within cooldown window, so there shouldn't be a second reconnect
        signalSource.getNextVoltage();

        // connect() called in constructor + in first handleDisconnection() = 2
        // second call is suppressed by 2 second cooldown
        verify(mockConnectionManager, times(2)).connect();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Shutdown
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("close() calls disconnect() on the underlying connection manager")
    void testCloseCallsDisconnect() {
        when(mockConnectionManager.isConnected()).thenReturn(true);

        signalSource = new HardwareSignalSource(mockConnectionManager, realParser);
        signalSource.close();

        verify(mockConnectionManager, times(1)).disconnect();
    }

}