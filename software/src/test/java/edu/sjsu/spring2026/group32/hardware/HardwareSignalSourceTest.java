package edu.sjsu.spring2026.group32.hardware;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HardwareSignalSource}.
 *
 * <p>All tests use a mocked {@link SerialConnectionManager} so no real serial
 * port or hardware is required.  The {@link NeuralSignalParser} is used as a
 * real instance (it has no external dependencies) to keep voltage conversion
 * logic honest.
 *
 * <h3>Architecture note</h3>
 * {@code HardwareSignalSource} receives voltage updates via a
 * {@link SerialConnectionManager.SerialListener} that it registers in its
 * constructor.  Tests capture that listener via an {@link ArgumentCaptor} and
 * fire callbacks manually to simulate incoming serial data without needing a
 * background reader thread.
 */
@DisplayName("HardwareSignalSource Suite")
class HardwareSignalSourceTest {

    private HardwareSignalSource signalSource;
    private SerialConnectionManager mockManager;
    private NeuralSignalParser realParser;

    @BeforeEach
    void setUp() {
        mockManager = mock(SerialConnectionManager.class);
        realParser  = new NeuralSignalParser();   // channel 0, 4095 max, 3.3 V, alpha=1.0
    }

    @AfterEach
    void tearDown() {
        if (signalSource != null) {
            signalSource.close();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Constructor behaviour
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Constructor skips auto-connect when port is already open")
    void testSkipsAutoConnectWhenAlreadyConnected() {
        when(mockManager.isConnected()).thenReturn(true);

        signalSource = new HardwareSignalSource(mockManager, realParser);

        verify(mockManager, never()).connect();
    }

    @Test
    @DisplayName("Constructor calls connect() when port is not open")
    void testCallsConnectWhenNotConnected() {
        when(mockManager.isConnected()).thenReturn(false);
        when(mockManager.connect()).thenReturn(false);

        signalSource = new HardwareSignalSource(mockManager, realParser);

        verify(mockManager, times(1)).connect();
    }

    @Test
    @DisplayName("Constructor registers a SerialListener with the manager")
    void testListenerRegisteredOnConstruction() {
        when(mockManager.isConnected()).thenReturn(true);

        signalSource = new HardwareSignalSource(mockManager, realParser);

        verify(mockManager, times(1))
                .addListener(any(SerialConnectionManager.SerialListener.class));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Voltage reading via listener callback 
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getNextVoltage() returns voltage delivered via onSample() callback")
    void testConnectionSuccessAndVoltageRead() {
        when(mockManager.isConnected()).thenReturn(true);

        ArgumentCaptor<SerialConnectionManager.SerialListener> listenerCaptor = ArgumentCaptor.forClass(SerialConnectionManager.SerialListener.class);

        signalSource = new HardwareSignalSource(mockManager, realParser);
        verify(mockManager).addListener(listenerCaptor.capture());

        String csvLine = "1000,0,0,4095";
        SerialConnectionManager.SampleFrame frame = new SerialConnectionManager.SampleFrame(csvLine, 1000L, 4095, null);

        listenerCaptor.getValue().onSample(frame);

        assertEquals(3.3, signalSource.getNextVoltage(), 0.001, "Voltage should reflect value delivered by onSample() callback");
    }

    @Test
    @DisplayName("getNextVoltage() returns 0.0 when no sample has arrived yet")
    void testGetNextVoltageDefaultsToZero() {
        when(mockManager.isConnected()).thenReturn(true);

        signalSource = new HardwareSignalSource(mockManager, realParser);

        assertEquals(0.0, signalSource.getNextVoltage(), 0.0001, "latestVoltage should be 0.0 before any sample arrives");
    }

    @Test
    @DisplayName("getNextVoltage() returns 0.0 when manager is disconnected")
    void testNullLineReturnsZero() {
        when(mockManager.isConnected()).thenReturn(false);
        when(mockManager.connect()).thenReturn(false);

        signalSource = new HardwareSignalSource(mockManager, realParser);

        assertEquals(0.0, signalSource.getNextVoltage(), 0.0001, "Should return 0.0 when not connected");
    }

    @Test
    @DisplayName("onDisconnected callback resets latestVoltage to 0.0")
    void testExceptionDuringReadTriggersDisconnect() {
        when(mockManager.isConnected()).thenReturn(true);

        ArgumentCaptor<SerialConnectionManager.SerialListener> listenerCaptor = ArgumentCaptor.forClass(SerialConnectionManager.SerialListener.class);

        signalSource = new HardwareSignalSource(mockManager, realParser);
        verify(mockManager).addListener(listenerCaptor.capture());

        String csvLine = "1000,0,0,4095";

        SerialConnectionManager.SampleFrame frame = new SerialConnectionManager.SampleFrame(csvLine, 1000L, 4095, null);
        
        listenerCaptor.getValue().onSample(frame);

        assertEquals(3.3, signalSource.getNextVoltage(), 0.001, "pre-condition: voltage is 3.3 V");

        // simulates a serial disconnect, onDisconnected() should reset voltage.
        listenerCaptor.getValue().onDisconnected("USB removed");

        when(mockManager.isConnected()).thenReturn(false);
        when(mockManager.connect()).thenReturn(false);

        assertEquals(0.0, signalSource.getNextVoltage(), 0.0001, "latestVoltage should reset to 0.0 after onDisconnected()");
    }

    @Test
    @DisplayName("getNextVoltage() seeds latestVoltage from getLatestSampleFrame() when already connected")
    void testSeedsVoltageFromLatestSampleOnConnect() {
        when(mockManager.isConnected()).thenReturn(true);

        String csvLine = "500,0,0,2048";   // raw = 2048 --> ~ 1.65 V
        SerialConnectionManager.SampleFrame frame = new SerialConnectionManager.SampleFrame(csvLine, 500L, 2048, null);
        
        when(mockManager.getLatestSampleFrame()).thenReturn(frame);

        signalSource = new HardwareSignalSource(mockManager, realParser);

        assertEquals(1.65, signalSource.getNextVoltage(), 0.01, "Should seed latestVoltage from getLatestSampleFrame() when already connected");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Missing hardware 
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing hardware is handled gracefully: getNextVoltage() returns 0.0")
    void testMissingHardwareHandling() {
        when(mockManager.isConnected()).thenReturn(false);
        when(mockManager.connect()).thenReturn(false);

        signalSource = new HardwareSignalSource(mockManager, realParser);

        double voltage = signalSource.getNextVoltage();

        assertEquals(0.0, voltage, 0.0001, "Should return 0.0 when hardware is absent");
        verify(mockManager, times(2)).connect();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Reconnection throttle
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Reconnect attempts are throttled by RECONNECT_COOLDOWN_MS")
    void testReconnectCooldownThrottlesAttempts() {
        when(mockManager.isConnected()).thenReturn(false);
        when(mockManager.connect()).thenReturn(false);

        signalSource = new HardwareSignalSource(mockManager, realParser);

        signalSource.getNextVoltage();
        signalSource.getNextVoltage();

        verify(mockManager, times(2)).connect();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VoltageInjector contract
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("injectVoltage(channel, volts) sends correctly formatted INJECT_V_CHx command")
    void testInjectVoltageSendsCorrectCommand() {
        when(mockManager.isConnected()).thenReturn(true);

        signalSource = new HardwareSignalSource(mockManager, realParser);
        signalSource.injectVoltage(1, 2.5);

        verify(mockManager, times(1)).sendLine("INJECT_V_CH1:2.500");
    }

    @Test
    @DisplayName("injectVoltage() throws IllegalArgumentException for channel < 1")
    void testInjectVoltageThrowsOnInvalidChannel() {
        when(mockManager.isConnected()).thenReturn(true);

        signalSource = new HardwareSignalSource(mockManager, realParser);

        assertThrows(IllegalArgumentException.class, () -> signalSource.injectVoltage(0, 1.0), "Channel 0 should be rejected by validateChannel()");
    }

    @Test
    @DisplayName("stopInjection(0) sends STOP_INJECT (all channels)")
    void testStopInjectionChannel0SendsStopInject() {
        when(mockManager.isConnected()).thenReturn(true);

        signalSource = new HardwareSignalSource(mockManager, realParser);
        signalSource.stopInjection(0);

        verify(mockManager, times(1)).sendLine("STOP_INJECT");
    }

    @Test
    @DisplayName("stopInjection(1) sends STOP_INJECT_CH1")
    void testStopInjectionChannel1SendsStopInjectCh1() {
        when(mockManager.isConnected()).thenReturn(true);

        signalSource = new HardwareSignalSource(mockManager, realParser);
        signalSource.stopInjection(1);

        verify(mockManager, times(1)).sendLine("STOP_INJECT_CH1");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Shutdown
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("close() removes the listener and calls disconnect() on the manager")
    void testCloseCallsDisconnect() {
        when(mockManager.isConnected()).thenReturn(true);

        signalSource = new HardwareSignalSource(mockManager, realParser);
        signalSource.close();

        verify(mockManager, times(1)).removeListener(any(SerialConnectionManager.SerialListener.class));
        verify(mockManager, times(1)).disconnect();
    }
}