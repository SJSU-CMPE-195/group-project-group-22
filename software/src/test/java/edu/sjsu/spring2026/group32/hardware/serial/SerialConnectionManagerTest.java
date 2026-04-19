package edu.sjsu.spring2026.group32.hardware.serial;

import org.junit.jupiter.api.*;
import java.io.ByteArrayInputStream;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Serial Connection Manager Suite")
class SerialConnectionManagerTest {

    private SerialConnectionManager connectionManager;
    private SerialDevice mockDevice;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockDevice = mock(SerialDevice.class);
        when(mockDevice.getDescriptivePortName()).thenReturn("Silicon Labs CP210x USB to UART Bridge");
        when(mockDevice.getSystemPortName()).thenReturn("COM3");
        when(mockDevice.openPort()).thenReturn(true);
        when(mockDevice.isOpen()).thenReturn(true);

        // feed the mock device a fake input stream
        String fakeStreamData = "first_line\nsecond_line\n";
        ByteArrayInputStream mockStream = new ByteArrayInputStream(fakeStreamData.getBytes());
        when(mockDevice.getInputStream()).thenReturn(mockStream);
    }

    // -------------------------------------------------------------------------
    // Auto-connect
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should successfully find port, connect, and read lines")
    void testSuccessfulConnectionAndRead() {
        // Simulates scanning USB ports returning the mock device
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};
        connectionManager = new SerialConnectionManager(supplier);

        boolean result = connectionManager.connect();
        String firstLine  = connectionManager.getNextLine();
        String secondLine = connectionManager.getNextLine();

        assertTrue(result);
        verify(mockDevice).setBaudRate(115200);
        // 1 = TIMEOUT_READ_SEMI_BLOCKING, 100ms read timeout, 0 = non-blocking write
        verify(mockDevice).setComPortTimeouts(1, 100, 0);
        assertEquals("first_line",  firstLine);
        assertEquals("second_line", secondLine);
    }

    @Test
    @DisplayName("Should return false when no compatible hardware is found")
    void testNoCompatibleHardware() {
        // Simulates no USB devices plugged in — connect() loops over nothing and returns false
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{};
        connectionManager = new SerialConnectionManager(supplier);

        boolean result = connectionManager.connect();
        String line    = connectionManager.getNextLine();

        assertFalse(result);
        // Scanner was never initialized since connect() failed
        assertNull(line);
    }

    @Test
    @DisplayName("connect() returns false when port provider throws an exception")
    void testConnectReturnsFalseWhenProviderThrows() {
        // Simulates a failure to enumerate USB ports (e.g. native library crash)
        connectionManager = new SerialConnectionManager(
            () -> { throw new RuntimeException("USB enumeration failed"); }
        );
        assertFalse(connectionManager.connect(),
            "connect() should return false when port provider throws");
    }

    @Test
    @DisplayName("Non-compatible port descriptor is skipped by connect()")
    void testNonCompatiblePortIsSkipped() {
        when(mockDevice.getDescriptivePortName()).thenReturn("Bluetooth Port (COM5)");
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};
        connectionManager = new SerialConnectionManager(supplier);

        boolean result = connectionManager.connect();

        assertFalse(result, "Non-compatible port should not be connected");
        verify(mockDevice, never()).openPort();
    }

    @Test
    @DisplayName("Should use a custom readTimeoutMs when supplied via extended constructor")
    void testCustomReadTimeout() {
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};
        connectionManager = new SerialConnectionManager(supplier, 1);
        connectionManager.connect();
        verify(mockDevice).setComPortTimeouts(1, 1, 0);
    }

    // -------------------------------------------------------------------------
    // Manual connection (connectTo)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("connectTo() opens the given port and configures baud rate / timeout")
    void testConnectTo() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        boolean result = connectionManager.connectTo(mockDevice);

        assertTrue(result, "connectTo() should return true when openPort() succeeds");
        verify(mockDevice).setBaudRate(115200);
        verify(mockDevice).setComPortTimeouts(1, 100, 0);
        verify(mockDevice).openPort();
    }

    @Test
    @DisplayName("connectTo() returns false and clears comPort when openPort() fails")
    void testConnectToFailsWhenPortCannotOpen() {
        when(mockDevice.openPort()).thenReturn(false);
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        boolean result = connectionManager.connectTo(mockDevice);

        assertFalse(result, "connectTo() should return false when openPort() fails");
        assertFalse(connectionManager.isConnected(),
            "Manager should not be connected after failed connectTo()");
    }

    // -------------------------------------------------------------------------
    // Connection state
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isConnected() returns false before any connection attempt")
    void testIsConnectedFalseInitially() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertFalse(connectionManager.isConnected());
    }

    @Test
    @DisplayName("isConnected() returns true after a successful connect()")
    void testIsConnectedTrueAfterConnect() {
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};
        connectionManager = new SerialConnectionManager(supplier);
        connectionManager.connect();
        assertTrue(connectionManager.isConnected());
    }

    @Test
    @DisplayName("isConnected() returns false after disconnect()")
    void testIsConnectedFalseAfterDisconnect() {
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};
        connectionManager = new SerialConnectionManager(supplier);
        connectionManager.connect();
        connectionManager.disconnect();
        assertFalse(connectionManager.isConnected());
    }

    // -------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getNextLine() returns null when stream is exhausted")
    void testGetNextLineReturnsNullWhenExhausted() {
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};
        connectionManager = new SerialConnectionManager(supplier);
        connectionManager.connect();

        assertNull(connectionManager.getNextLine(),
            "getNextLine() should return null when stream is empty");
    }

    @Test
    @DisplayName("getInputStream() returns null when not connected")
    void testGetInputStreamReturnsNullWhenNotConnected() {
        // comPort is null before any connection — getInputStream() should not throw
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertNull(connectionManager.getInputStream(),
            "getInputStream() should return null when not connected");
    }

    // -------------------------------------------------------------------------
    // Write helper
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendLine() does not throw when lineWriter is null (not connected)")
    void testSendLineDoesNotThrowWhenNotConnected() {
        // lineWriter is only initialized after a successful connection
        // calling sendLine() before connecting should be a safe no-op
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertDoesNotThrow(() -> connectionManager.sendLine("TEST"),
            "sendLine() should silently do nothing when not connected");
    }

    // -------------------------------------------------------------------------
    // Disconnect
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should properly close open ports on disconnect")
    void testDisconnect() {
        // disconnect() is a no-op if connect() was never called first
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};
        connectionManager = new SerialConnectionManager(supplier);
        connectionManager.connect();
        connectionManager.disconnect();

        verify(mockDevice, times(1)).closePort();
    }

    @Test
    @DisplayName("disconnect() resets device name and channel count to defaults")
    void testDisconnectResetsDeviceInfo() {
        String handshake = "#INFO:NeuralSignal,CH=2\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(handshake.getBytes()));
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};

        connectionManager = new SerialConnectionManager(supplier);
        connectionManager.connect();
        connectionManager.readInfoHandshake(5);
        connectionManager.disconnect();

        assertEquals("", connectionManager.getDeviceName());
        assertEquals(0,  connectionManager.getDeviceChannelCount());
    }

    // -------------------------------------------------------------------------
    // Device handshake
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("readInfoHandshake() returns false when not connected")
    void testReadInfoHandshakeReturnsFalseWhenNotConnected() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertFalse(connectionManager.readInfoHandshake(5));
    }

    @Test
    @DisplayName("readInfoHandshake() parses #INFO: line and populates device name and channel count")
    void testReadInfoHandshakeParsesInfoLine() {
        String handshake = "#INFO:NeuralSignal,CH=2\n";
        when(mockDevice.getInputStream()).thenReturn( new ByteArrayInputStream(handshake.getBytes()));
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};
        connectionManager = new SerialConnectionManager(supplier);
        connectionManager.connect();

        boolean result = connectionManager.readInfoHandshake(5);

        assertTrue(result);

        assertEquals("NeuralSignal", connectionManager.getDeviceName());
        assertEquals(2, connectionManager.getDeviceChannelCount());
    }

    @Test
    @DisplayName("readInfoHandshake() returns false when maxAttempts exhausted without INFO line")
    void testReadInfoHandshakeReturnsFalseWhenNoInfoLine() {
        // stream returns non-INFO lines — handshake should fail after maxAttempts
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream("some,data,line\nmore,data\n".getBytes()));

        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};

        connectionManager = new SerialConnectionManager(supplier);
        connectionManager.connect();

        assertFalse(connectionManager.readInfoHandshake(3), "Should return false when no #INFO: line found within maxAttempts");
        assertEquals("", connectionManager.getDeviceName(), "deviceName should remain empty when handshake fails");
        assertEquals(0, connectionManager.getDeviceChannelCount(), "deviceChannelCount should remain 0 when handshake fails");
        
    }
}