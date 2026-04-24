package edu.sjsu.spring2026.group32.hardware.serial;

import org.junit.jupiter.api.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
        when(mockDevice.getOutputStream()).thenReturn(new ByteArrayOutputStream());
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

    @Test
    @DisplayName("Closing getInputStream() wrapper does not close the launcher-owned port")
    void testGetInputStreamCloseDoesNotDisconnectSharedPort() throws Exception {
        class CloseTrackingInputStream extends ByteArrayInputStream {
            private boolean closeCalled;

            CloseTrackingInputStream(byte[] data) {
                super(data);
            }

            @Override
            public void close() throws java.io.IOException {
                closeCalled = true;
                super.close();
            }
        }

        CloseTrackingInputStream trackingStream =
                new CloseTrackingInputStream("first_line\n".getBytes());
        when(mockDevice.getInputStream()).thenReturn(trackingStream);

        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);

        InputStream borrowedStream = connectionManager.getInputStream();
        assertNotNull(borrowedStream, "Connected manager should expose a readable stream view");

        borrowedStream.close();

        assertFalse(trackingStream.closeCalled,
                "Closing the borrowed stream must not close the underlying serial stream");
        assertTrue(connectionManager.isConnected(),
                "Closing the borrowed stream must not disconnect the shared connection");
        verify(mockDevice, never()).closePort();
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

    @Test
    @DisplayName("disconnect() sends STOP_INJECT before closing the port")
    void testDisconnectStopsInjectionBeforeClose() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(mockDevice.getOutputStream()).thenReturn(output);

        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};
        connectionManager = new SerialConnectionManager(supplier);
        connectionManager.connect();

        connectionManager.disconnect();

        assertTrue(output.toString().contains("STOP_INJECT"),
            "disconnect() must send STOP_INJECT so the firmware is not left injecting");
        verify(mockDevice, times(1)).closePort();
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

    // -------------------------------------------------------------------------
    // Heartbeat — lastRxMs / isRxTimedOut
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getLastRxMs() returns 0 before any connection")
    void testLastRxMsIsZeroBeforeConnection() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertEquals(0, connectionManager.getLastRxMs(),
            "lastRxMs should be 0 before any connection is made");
    }

    @Test
    @DisplayName("getLastRxMs() remains 0 after connectTo() — heartbeat is not seeded at connect time")
    void testLastRxMsNotSeededAfterConnectTo() {
        // The seed was intentionally removed so the idle Launcher watchdog does not
        // fire isRxTimedOut() before any program has started reading.
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);

        assertEquals(0, connectionManager.getLastRxMs(),
            "lastRxMs should stay 0 after connectTo(); it only advances when a real consumer reads");
    }

    @Test
    @DisplayName("getLastRxMs() advances each time getNextLine() returns a line")
    void testLastRxMsUpdatesOnSuccessfulRead() throws InterruptedException {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
        connectionManager.connect();

        long afterConnect = connectionManager.getLastRxMs();
        Thread.sleep(5); // ensure wall-clock advances before the next read
        connectionManager.getNextLine(); // reads "first_line"

        assertTrue(connectionManager.getLastRxMs() >= afterConnect,
            "lastRxMs should be updated after a successful line read");
    }

    @Test
    @DisplayName("getLastRxMs() does not advance when getNextLine() returns null (empty stream)")
    void testLastRxMsDoesNotUpdateOnEmptyRead() throws InterruptedException {
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
        connectionManager.connect();

        long afterConnect = connectionManager.getLastRxMs();
        Thread.sleep(5);
        connectionManager.getNextLine(); // stream empty → null

        assertEquals(afterConnect, connectionManager.getLastRxMs(),
            "lastRxMs should not change when getNextLine() returns null");
    }

    @Test
    @DisplayName("isRxTimedOut() returns false when never connected (lastRxMs is 0)")
    void testIsRxTimedOutFalseWhenNeverConnected() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertFalse(connectionManager.isRxTimedOut(),
            "isRxTimedOut() should return false when never connected");
    }

    @Test
    @DisplayName("isRxTimedOut() returns false immediately after connection (heartbeat just seeded)")
    void testIsRxTimedOutFalseRightAfterConnect() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);

        assertFalse(connectionManager.isRxTimedOut(),
            "isRxTimedOut() should be false immediately after connecting");
    }

    @Test
    @DisplayName("isRxTimedOut() returns true when lastRxMs is older than 3 seconds")
    void testIsRxTimedOutTrueWhenStale() throws Exception {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);

        // Backdate lastRxMs via reflection to simulate 5 seconds of silence
        java.lang.reflect.Field field = SerialConnectionManager.class.getDeclaredField("lastRxMs");
        field.setAccessible(true);
        field.setLong(connectionManager, System.currentTimeMillis() - 5_000);

        assertTrue(connectionManager.isRxTimedOut(),
            "isRxTimedOut() should return true when no data received for > 3 seconds");
    }

    // -------------------------------------------------------------------------
    // Heartbeat — readInfoHandshake / refreshHeartbeat interaction
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("readInfoHandshake() does not update lastRxMs — handshake reads are not consumer activity")
    void testReadInfoHandshakeDoesNotUpdateLastRxMs() {
        // Root cause of the 3-second idle-Launcher disconnect bug:
        // readInfoHandshake() was calling getNextLine(), which seeded lastRxMs.
        // The fix uses readNextLineRaw() internally, which skips the heartbeat update.
        String handshake = "#INFO:NeuralSignal,CH=1\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(handshake.getBytes()));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);

        connectionManager.readInfoHandshake(5);

        assertEquals(0, connectionManager.getLastRxMs(),
            "readInfoHandshake() must not update lastRxMs; only real consumer reads should start the clock");
    }

    @Test
    @DisplayName("isRxTimedOut() stays false after connectTo() + readInfoHandshake() — no spurious watchdog disconnect")
    void testIsRxTimedOutFalseAfterConnectAndHandshake() {
        // Regression test for the bug where the idle Launcher disconnected after 3 s.
        // Sequence: connect → handshake → no program running → watchdog must NOT fire.
        String handshake = "#INFO:NeuralSignal,CH=1\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(handshake.getBytes()));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);
        connectionManager.readInfoHandshake(5);

        assertFalse(connectionManager.isRxTimedOut(),
            "isRxTimedOut() must remain false after connect + handshake so the idle Launcher watchdog does not disconnect");
    }

    @Test
    @DisplayName("refreshHeartbeat() updates lastRxMs to approximately the current wall-clock time")
    void testRefreshHeartbeatUpdatesLastRxMs() throws InterruptedException {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);

        long before = System.currentTimeMillis();
        Thread.sleep(2); // ensure wall-clock has advanced
        connectionManager.refreshHeartbeat();
        long after = System.currentTimeMillis();

        long ts = connectionManager.getLastRxMs();
        assertTrue(ts >= before && ts <= after,
            "refreshHeartbeat() should set lastRxMs to the current wall-clock time");
    }

    @Test
    @DisplayName("clearHeartbeat() resets lastRxMs to 0 so an idle shared manager does not time out")
    void testClearHeartbeatResetsLastRxMs() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);
        connectionManager.refreshHeartbeat();

        connectionManager.clearHeartbeat();

        assertEquals(0, connectionManager.getLastRxMs(),
            "clearHeartbeat() should return the manager to its idle no-consumer state");
        assertFalse(connectionManager.isRxTimedOut(),
            "An idle shared manager must not time out after its consumer stops");
    }

    @Test
    @DisplayName("isRxTimedOut() returns true after refreshHeartbeat() if that timestamp is subsequently backdated")
    void testIsRxTimedOutTrueAfterRefreshWhenStale() throws Exception {
        // Verifies that once a real consumer has started (refreshHeartbeat called),
        // the timeout can still fire if data stops — e.g. USB removed mid-session.
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);
        connectionManager.refreshHeartbeat(); // simulate consumer starting

        // Backdate to simulate 5 seconds of silence after a consumer was active
        java.lang.reflect.Field field = SerialConnectionManager.class.getDeclaredField("lastRxMs");
        field.setAccessible(true);
        field.setLong(connectionManager, System.currentTimeMillis() - 5_000);

        assertTrue(connectionManager.isRxTimedOut(),
            "isRxTimedOut() should return true when data has been silent for > 3 s after a consumer was active");
    }

    // -------------------------------------------------------------------------
    // getNextLine() — self-disconnect on IOException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getNextLine() disconnects and returns null when the stream throws IOException")
    void testGetNextLineDisconnectsOnIOException() {
        // A stream that immediately throws IOException — simulates a yanked USB cable
        java.io.InputStream failingStream = new java.io.InputStream() {
            @Override public int read() throws java.io.IOException {
                throw new java.io.IOException("Simulated USB removal");
            }
        };
        when(mockDevice.getInputStream()).thenReturn(failingStream);
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
        connectionManager.connect();

        String line = connectionManager.getNextLine();

        assertNull(line, "getNextLine() should return null when the stream throws IOException");
        assertFalse(connectionManager.isConnected(),
            "Manager should be disconnected after a stream IOException");
    }
}
