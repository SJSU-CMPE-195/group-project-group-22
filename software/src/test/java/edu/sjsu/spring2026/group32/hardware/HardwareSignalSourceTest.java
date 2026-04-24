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
    @DisplayName("[TODO] Should successfully connect, configure port, and read voltage")
    void testConnectionSuccessAndVoltageRead() {
        // TODO: implement
        // NOTE: HardwareSignalSource no longer calls getNextLine() directly.
        // Voltage is now delivered via the SerialListener.onSample() callback registered
        // in the constructor. Rewrite this test to fire the listener callback manually
        // (capture the registered SerialListener via verify + ArgumentCaptor, then call
        // onSample() with a fake SampleFrame) and assert the returned voltage.
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
    @DisplayName("[TODO] getNextVoltage() returns 0.0 and triggers disconnect after a read exception")
    void testExceptionDuringReadTriggersDisconnect() {
        // TODO: implement
        // NOTE: getNextVoltage() no longer calls getNextLine() and therefore can no longer
        // catch a RuntimeException from it. Read errors are now handled inside the
        // background reader thread (SerialConnectionManager), which calls disconnect()
        // on IOException via disconnectInternal(). Rewrite this test to simulate a serial
        // read error at the SerialConnectionManager level (e.g. via the onDisconnected
        // listener callback) and assert that latestVoltage is reset to 0.0.
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