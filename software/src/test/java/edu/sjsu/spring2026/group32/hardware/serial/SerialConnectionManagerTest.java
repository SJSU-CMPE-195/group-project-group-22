package edu.sjsu.spring2026.group32.hardware.serial;

import org.junit.jupiter.api.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * Unit tests for {@link SerialConnectionManager}.
 *
 * <p>Several background-reader behaviours (sequential line reads, IOException
 * self-disconnect, getInputStream-close isolation) are inherently
 * non-deterministic in the listener-driven architecture and are intentionally
 * omitted or replaced with listener-based synchronisation via
 * {@link CountDownLatch}.
 */
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
 
        String fakeStreamData = "1000,0,0,2048\n1001,0,0,4095\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(fakeStreamData.getBytes()));
        when(mockDevice.getOutputStream()).thenReturn(new ByteArrayOutputStream());

    }
 
    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.disconnect();
        }
    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // Auto-connect
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("connect() returns true, configures baud/timeout, and opens the port")
    void testSuccessfulConnectionAndRead() {
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};
        connectionManager = new SerialConnectionManager(supplier, 100);
 
        boolean result = connectionManager.connect();
 
        assertTrue(result, "connect() should return true for a compatible port");

        verify(mockDevice).setBaudRate(115200);
        verify(mockDevice).setComPortTimeouts(1, 100, 0);
        verify(mockDevice).openPort();

        assertTrue(connectionManager.isConnected());
    }
 
    @Test
    @DisplayName("connect() returns false when no compatible hardware is found")
    void testNoCompatibleHardware() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
 
        boolean result = connectionManager.connect();
 
        assertFalse(result);
        assertNull(connectionManager.getNextLine());
    }
 
    @Test
    @DisplayName("connect() returns false when port provider throws an exception")
    void testConnectReturnsFalseWhenProviderThrows() {
        connectionManager = new SerialConnectionManager(() -> { throw new RuntimeException("USB enumeration failed"); });
        assertFalse(connectionManager.connect(), "connect() should return false when port provider throws");
    }
 
    @Test
    @DisplayName("Non-compatible port descriptor is skipped by connect()")
    void testNonCompatiblePortIsSkipped() {
        when(mockDevice.getDescriptivePortName()).thenReturn("Bluetooth Port (COM5)");
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
 
        boolean result = connectionManager.connect();
 
        assertFalse(result, "Non-compatible port should not be connected");
        verify(mockDevice, never()).openPort();
    }
 
    @Test
    @DisplayName("connect() uses custom readTimeoutMs when supplied via extended constructor")
    void testCustomReadTimeout() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 1);
        connectionManager.connect();
        verify(mockDevice).setComPortTimeouts(1, 1, 0);
    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // Manual connection (connectTo)
    // ──────────────────────────────────────────────────────────────────────────
 
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
        assertFalse(connectionManager.isConnected(),  "Manager should not be connected after failed connectTo()");
    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // Connection state
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("isConnected() returns false before any connection attempt")
    void testIsConnectedFalseInitially() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertFalse(connectionManager.isConnected());
    }
 
    @Test
    @DisplayName("isConnected() returns true after a successful connect()")
    void testIsConnectedTrueAfterConnect() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
        connectionManager.connect();

        assertTrue(connectionManager.isConnected());
    }
 
    @Test
    @DisplayName("isConnected() returns false after disconnect()")
    void testIsConnectedFalseAfterDisconnect() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
        connectionManager.connect();
        connectionManager.disconnect();

        assertFalse(connectionManager.isConnected());
    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // Reading
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("getNextLine() returns null when stream is exhausted and no sample has arrived")
    void testGetNextLineReturnsNullWhenExhausted() {
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
        connectionManager.connect();
 
        assertNull(connectionManager.getNextLine(), "getNextLine() should return null when stream is empty");
    }
 
    @Test
    @DisplayName("getInputStream() returns null when not connected")
    void testGetInputStreamReturnsNullWhenNotConnected() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertNull(connectionManager.getInputStream(), "getInputStream() should return null when not connected");
    }
 
    // getInputStream() now returns the raw InputStream directly (no wrapper),
    // so closing it closes the underlying ByteArrayInputStream — the isolation
    // guarantee no longer applies in the listener-driven architecture.
    // That test has been removed; the contract changed intentionally.
 
    // ──────────────────────────────────────────────────────────────────────────
    // sendLine()
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("sendLine() does not throw when lineWriter is null (not connected)")
    void testSendLineDoesNotThrowWhenNotConnected() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertDoesNotThrow(() -> connectionManager.sendLine("TEST"), "sendLine() should silently do nothing when not connected");
    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // Disconnect
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("disconnect() closes the open port")
    void testDisconnect() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
        connectionManager.connect();
        connectionManager.disconnect();
 
        verify(mockDevice, times(1)).closePort();
    }
 
    @Test
    @DisplayName("disconnect() resets device name and channel count to defaults")
    void testDisconnectResetsDeviceInfo() {
        String handshake = "#INFO:NeuralSignal,CH=2\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(handshake.getBytes()));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});

        connectionManager.connect();
        connectionManager.readInfoHandshake(5);
        connectionManager.disconnect();
 
        assertEquals("", connectionManager.getDeviceName());
        assertEquals(0, connectionManager.getDeviceChannelCount());
    }
 
    @Test
    @DisplayName("disconnect() sends STOP_INJECT before closing the port")
    void testDisconnectStopsInjectionBeforeClose() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(mockDevice.getOutputStream()).thenReturn(output);
 
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
        connectionManager.connect();
        connectionManager.disconnect();
 
        assertTrue(output.toString().contains("STOP_INJECT"), "disconnect() must send STOP_INJECT before closing the port");
        verify(mockDevice, times(1)).closePort();
    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // Device handshake
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("readInfoHandshake() returns false when not connected")
    void testReadInfoHandshakeReturnsFalseWhenNotConnected() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertFalse(connectionManager.readInfoHandshake(5));

    }
 
    @Test
    @DisplayName("readInfoHandshake() parses #INFO: line and populates device name and channel count")
    void testReadInfoHandshakeParsesInfoLine() throws InterruptedException {
        String handshake = "#INFO:NeuralSignal,CH=2\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(handshake.getBytes()));
 
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 100);
 
        CountDownLatch latch = new CountDownLatch(1);
        connectionManager.addListener(new SerialConnectionManager.SerialListener() {
            @Override
            public void onInfoUpdated(String deviceName, int channelCount) {
                latch.countDown();

            }

        });
 
        connectionManager.connect();
 
        assertTrue(latch.await(2, TimeUnit.SECONDS), "onInfoUpdated() should fire within 2 s after connect()");

        assertEquals("NeuralSignal", connectionManager.getDeviceName());
        assertEquals(2, connectionManager.getDeviceChannelCount());

    }
 
    @Test
    @DisplayName("readInfoHandshake() returns false when maxAttempts exhausted without INFO line")
    void testReadInfoHandshakeReturnsFalseWhenNoInfoLine() {
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream("some,data,line\nmore,data\n".getBytes()));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});

        connectionManager.connect();
 
        assertFalse(connectionManager.readInfoHandshake(3), "Should return false when no #INFO: line found within maxAttempts");
        assertEquals("", connectionManager.getDeviceName(), "deviceName should remain empty when handshake fails");
        assertEquals(0, connectionManager.getDeviceChannelCount(), "deviceChannelCount should remain 0 when handshake fails");
    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // Heartbeat — lastRxMs / isRxTimedOut
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("getLastRxMs() returns 0 before any connection")
    void testLastRxMsIsZeroBeforeConnection() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertEquals(0, connectionManager.getLastRxMs());
    }
 
    @Test
    @DisplayName("getLastRxMs() remains 0 after connectTo() — heartbeat is not seeded at connect time")
    void testLastRxMsNotSeededAfterConnectTo() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);
 
        assertEquals(0, connectionManager.getLastRxMs(), "lastRxMs should stay 0 after connectTo() before any data arrives");
    }
 
    @Test
    @DisplayName("getLastRxMs() advances after background reader processes a line")
    void testLastRxMsUpdatesOnSuccessfulRead() throws InterruptedException {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
 
        CountDownLatch latch = new CountDownLatch(1);
        connectionManager.addListener(new SerialConnectionManager.SerialListener() {
            @Override
            public void onSampleLine(String line) {
                latch.countDown();
            }
        });
 
        connectionManager.connect();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Background reader should deliver a sample within 2 s");
 
        assertTrue(connectionManager.getLastRxMs() > 0, "lastRxMs should be non-zero after a line is read by the background reader");
    }
 
    @Test
    @DisplayName("getLastRxMs() stays 0 when stream is empty (no lines to read)")
    void testLastRxMsDoesNotUpdateOnEmptyRead() throws InterruptedException {
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice});
        connectionManager.connect();
 
        Thread.sleep(50); // give reader thread time to exhaust the empty stream

        assertEquals(0, connectionManager.getLastRxMs(),"lastRxMs should not change when stream produces no lines");
    }
 
    @Test
    @DisplayName("isRxTimedOut() returns false when never connected (lastRxMs is 0)")
    void testIsRxTimedOutFalseWhenNeverConnected() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{});
        assertFalse(connectionManager.isRxTimedOut());
    }
 
    @Test
    @DisplayName("isRxTimedOut() returns false immediately after connectTo()")
    void testIsRxTimedOutFalseRightAfterConnect() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);
 
        assertFalse(connectionManager.isRxTimedOut());
    }
 
    @Test
    @DisplayName("isRxTimedOut() returns true when lastRxMs is older than 3 seconds")
    void testIsRxTimedOutTrueWhenStale() throws Exception {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);
 
        java.lang.reflect.Field field = SerialConnectionManager.class.getDeclaredField("lastRxMs");

        field.setAccessible(true);
        field.setLong(connectionManager, System.currentTimeMillis() - 5_000);
 
        assertTrue(connectionManager.isRxTimedOut(), "isRxTimedOut() should return true when no data received for > 3 seconds");
    }
 
    @Test
    @DisplayName("isRxTimedOut() stays false after connectTo() + readInfoHandshake()")
    void testIsRxTimedOutFalseAfterConnectAndHandshake() {
        String handshake = "#INFO:NeuralSignal,CH=1\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(handshake.getBytes()));

        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);

        connectionManager.connectTo(mockDevice);
        connectionManager.readInfoHandshake(5);
 
        assertFalse(connectionManager.isRxTimedOut(), "isRxTimedOut() must remain false after connect + handshake");
    }
 
    @Test
    @DisplayName("refreshHeartbeat() updates lastRxMs to approximately the current wall-clock time")
    void testRefreshHeartbeatUpdatesLastRxMs() throws InterruptedException {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);
 
        long before = System.currentTimeMillis();
        Thread.sleep(2);

        connectionManager.refreshHeartbeat();
        long after = System.currentTimeMillis();
 
        long ts = connectionManager.getLastRxMs();
        assertTrue(ts >= before && ts <= after, "refreshHeartbeat() should set lastRxMs to the current wall-clock time");
    }
 
    @Test
    @DisplayName("clearHeartbeat() resets lastRxMs to 0")
    void testClearHeartbeatResetsLastRxMs() {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);
        connectionManager.refreshHeartbeat();
 
        connectionManager.clearHeartbeat();
 
        assertEquals(0, connectionManager.getLastRxMs(), "clearHeartbeat() should reset lastRxMs to 0");
        assertFalse(connectionManager.isRxTimedOut(), "An idle shared manager must not time out after clearHeartbeat()");
    }
 
    @Test
    @DisplayName("isRxTimedOut() returns true after refreshHeartbeat() if timestamp is backdated")
    void testIsRxTimedOutTrueAfterRefreshWhenStale() throws Exception {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{}, 100);
        connectionManager.connectTo(mockDevice);
        connectionManager.refreshHeartbeat();
 
        java.lang.reflect.Field field = SerialConnectionManager.class.getDeclaredField("lastRxMs");

        field.setAccessible(true);
        field.setLong(connectionManager, System.currentTimeMillis() - 5_000);
 
        assertTrue(connectionManager.isRxTimedOut(), "isRxTimedOut() should return true when data has been silent for > 3 s");
    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // Listener API — addListener / removeListener
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("onSample callback is invoked when the reader receives a valid CSV sample line")
    void addListenerReceivesOnSampleCallback() throws InterruptedException {
        // Valid ESP32 CSV: millis, ?, ?, primaryRaw  (4 fields minimum)
        String csv = "1000,0,0,2048\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 100);
 
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SerialConnectionManager.SampleFrame> captured = new AtomicReference<>();
 
        connectionManager.addListener(new SerialConnectionManager.SerialListener() {
            @Override
            public void onSample(SerialConnectionManager.SampleFrame frame) {
                captured.set(frame);
                latch.countDown();

            }
        });
 
        connectionManager.connect();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "onSample() should be invoked within 2 s");
        assertNotNull(captured.get());
        assertEquals(2048, captured.get().primaryRaw());
    }
 
    @Test
    @DisplayName("onSampleLine callback is invoked with the raw line string")
    void addListenerReceivesOnSampleLineCallback() throws InterruptedException {
        String csv = "1000,0,0,512\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 100);
 
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> captured = new AtomicReference<>();
 
        connectionManager.addListener(new SerialConnectionManager.SerialListener() {
            @Override
            public void onSampleLine(String line) {
                captured.set(line);
                latch.countDown();
            }
        });
 
        connectionManager.connect();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "onSampleLine() should be invoked within 2 s");
        assertEquals("1000,0,0,512", captured.get());

    }
 
    @Test
    @DisplayName("onStatusPayload callback is invoked when a STATUS, line is received")
    void addListenerReceivesOnStatusPayloadCallback() throws InterruptedException {
        String statusLine = "STATUS,IDLE\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(statusLine.getBytes()));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 100);
 
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> captured = new AtomicReference<>();
 
        connectionManager.addListener(new SerialConnectionManager.SerialListener() {
            @Override
            public void onStatusPayload(String payload) {
                captured.set(payload);
                latch.countDown();
            }
        });
 
        connectionManager.connect();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "onStatusPayload() should be invoked within 2 s");
        assertEquals("IDLE", captured.get());

    }
 
    @Test
    @DisplayName("onInfoUpdated callback is invoked after a #INFO: line is parsed")
    void addListenerReceivesOnInfoUpdatedCallback() throws InterruptedException {
        String infoLine = "#INFO:TestDevice,CH=3\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(infoLine.getBytes()));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 100);
 
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> name    = new AtomicReference<>();
        AtomicReference<Integer> chCount = new AtomicReference<>();
 
        connectionManager.addListener(new SerialConnectionManager.SerialListener() {
            @Override
            public void onInfoUpdated(String deviceName, int channelCount) {
                name.set(deviceName);
                chCount.set(channelCount);
                latch.countDown();
            }
        });
 
        connectionManager.connect();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "onInfoUpdated() should be invoked within 2 s");
        assertEquals("TestDevice", name.get());
        assertEquals(3, chCount.get());
    }
 
    @Test
    @DisplayName("onDisconnected callback is invoked when disconnect() is called")
    void addListenerReceivesOnDisconnectedCallback() throws InterruptedException {
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 100);
        connectionManager.connect();
 
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> reason = new AtomicReference<>();
 
        connectionManager.addListener(new SerialConnectionManager.SerialListener() {
            @Override
            public void onDisconnected(String r) {
                reason.set(r);
                latch.countDown();
            }
        });
 
        connectionManager.disconnect();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "onDisconnected() should be invoked within 2 s of disconnect()");
        assertNotNull(reason.get());
    }
 
    @Test
    @DisplayName("removeListener() — removed listener no longer receives callbacks")
    void removeListenerStopsCallbacks() throws InterruptedException {
        String csv = "1000,0,0,100\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 100);
 
        // Use a second listener as a synchronisation signal so we know the reader
        // has processed the line — then verify the removed one was never called.
        CountDownLatch syncLatch = new CountDownLatch(1);
        AtomicReference<Boolean> removedCalled = new AtomicReference<>(false);
 
        SerialConnectionManager.SerialListener removed =
            new SerialConnectionManager.SerialListener() {
                @Override public void onSampleLine(String line) {
                    removedCalled.set(true);
                }
            };
 
        SerialConnectionManager.SerialListener sync =
            new SerialConnectionManager.SerialListener() {
                @Override public void onSampleLine(String line) {
                    syncLatch.countDown();
                }
            };
 
        connectionManager.addListener(removed);
        connectionManager.removeListener(removed);   // remove before connect
        connectionManager.addListener(sync);
 
        connectionManager.connect();
        assertTrue(syncLatch.await(2, TimeUnit.SECONDS), "Sync listener should fire within 2 s");
 
        assertFalse(removedCalled.get(),"Removed listener must not receive any callbacks");
    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // getLatestSampleFrame()
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("getLatestSampleFrame() returns null before any sample has arrived")
    void getLatestSampleFrameReturnsNullBeforeAnyRead() {
        when(mockDevice.getInputStream())
                .thenReturn(new ByteArrayInputStream(new byte[0]));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 100);
        connectionManager.connect();
 
        assertNull(connectionManager.getLatestSampleFrame(), "getLatestSampleFrame() should be null before any CSV sample is parsed");
    }
 
    @Test
    @DisplayName("getLatestSampleFrame() is populated after background reader processes a valid CSV line")
    void getLatestSampleFramePopulatedAfterReaderParsesLine() throws InterruptedException {
        String csv = "999,0,0,1024\n";
        when(mockDevice.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 100);
 
        CountDownLatch latch = new CountDownLatch(1);
        connectionManager.addListener(new SerialConnectionManager.SerialListener() {
            @Override public void onSample(SerialConnectionManager.SampleFrame f) {
                latch.countDown();
            }
        });
 
        connectionManager.connect();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
 
        SerialConnectionManager.SampleFrame frame = connectionManager.getLatestSampleFrame();

        assertNotNull(frame);
        assertEquals(1024, frame.primaryRaw());
        assertEquals(999L, frame.millis());

    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // stopAllInjection()
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("stopAllInjection() sends STOP_INJECT to the serial output stream")
    void stopAllInjectionSendsStopInjectCommand() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(mockDevice.getOutputStream()).thenReturn(output);
 
        connectionManager = new SerialConnectionManager(() -> new SerialDevice[]{mockDevice}, 100);
        connectionManager.connect();
 
        connectionManager.stopAllInjection();
 
        assertTrue(output.toString().contains("STOP_INJECT"), "stopAllInjection() should write STOP_INJECT to the output stream");
    }
}
