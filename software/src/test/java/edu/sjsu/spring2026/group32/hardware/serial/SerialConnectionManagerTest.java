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

        // Feed the mock device a fake input stream
        String fakeStreamData = "first_line\nsecond_line\n";
        ByteArrayInputStream mockStream = new ByteArrayInputStream(fakeStreamData.getBytes());
        when(mockDevice.getInputStream()).thenReturn(mockStream);
    }

    @Test
    @DisplayName("Should successfully find port, connect, and read lines")
    void testSuccessfulConnectionAndRead() {

        // Simulates scanning USB ports returning the mock device
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{mockDevice};

        // Simulates injecting the supplier so the manager doesn't touch the real USB ports
        connectionManager = new SerialConnectionManager(supplier);

        // Connects and reads two lines from simulated stream
        boolean result = connectionManager.connect();
        String firstLine = connectionManager.getNextLine();
        String secondLine = connectionManager.getNextLine();

        // Asserts connect() found a compatible port and opened it successfully
        assertTrue(result);

        // Verifies Baud rate was configured before opening the port
        verify(mockDevice).setBaudRate(115200);

        // Verifies timeout was configured before opening port 
        // 1 = TIMEOUT_READ_SEMI_BLOCKING, 100 (ms) = read timeout, 0 = non-blocking write timeout
        verify(mockDevice).setComPortTimeouts(1, 100, 0);

        // Verifies scanner correctly reads lines from the simulated input stream
        assertEquals("first_line", firstLine);
        assertEquals("second_line", secondLine);

    }

    @Test
    @DisplayName("Should return false when no compatible hardware is found")
    void testNoCompatibleHardware() {
        // Simulates serial device having no USB devices plugged in (based on empty array)
        // connect() loops over nothing and falls through to return false
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{};
        connectionManager = new SerialConnectionManager(supplier);

        // Attempts to connect and read with no hardware available
        boolean result = connectionManager.connect();
        String line = connectionManager.getNextLine();

        // Connect() found no compatible ports so it returns false
        assertFalse(result);

        // Scanner was never initialized since connect() failed, getNextLine() should return null immediately
        assertNull(line);

    }

    @Test
    @DisplayName("Should properly close open ports on disconnect")
    void testDisconnect() {
        // TODO (Arrange): Instantiate the manager with a supplier containing 'mockDevice', and call connect() so it is open.

        // TODO (Act): Call disconnect() on the manager.

        // TODO (Assert): Use Mockito.verify() to ensure that closePort() was called on 'mockDevice' exactly 1 time.
    }
}