package edu.sjsu.spring2026.group32.testsupport;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.serial.SerialDevice;
import org.mockito.Mockito;

import java.util.function.Supplier;

/**
 * Convenience Mockito helpers for tests that mix mocks with fake serial hardware.
 */
public final class MockitoHardwareSupport {
    private MockitoHardwareSupport() {
    }

    public static Supplier<SerialDevice[]> supplier(FakeSerialDevice... devices) {
        return () -> devices;
    }

    public static SerialConnectionManager mockConnectedManager(String deviceName, int channelCount) {
        SerialConnectionManager manager = Mockito.mock(SerialConnectionManager.class);
        Mockito.when(manager.isConnected()).thenReturn(true);
        Mockito.when(manager.getDeviceName()).thenReturn(deviceName);
        Mockito.when(manager.getDeviceChannelCount()).thenReturn(channelCount);
        return manager;
    }

    public static SerialConnectionManager mockDisconnectedManager() {
        SerialConnectionManager manager = Mockito.mock(SerialConnectionManager.class);
        Mockito.when(manager.isConnected()).thenReturn(false);
        Mockito.when(manager.getDeviceName()).thenReturn("");
        Mockito.when(manager.getDeviceChannelCount()).thenReturn(0);
        return manager;
    }
}
