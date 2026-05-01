package edu.sjsu.spring2026.group32.bidirectionaltest;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ActiveDeviceVoltageInjector}.
 *
 * <p>{@code ActiveDeviceVoltageInjector} is package-private; tests live in
 * the same package so they have direct access to the class under test.</p>
 *
 * <p>A {@link SerialConnectionManager} mock is used to verify that the
 * correct protocol strings are sent to the serial output without requiring
 * real hardware.</p>
 */
@DisplayName("ActiveDeviceVoltageInjector Suite")
class ActiveDeviceVoltageInjectorTest {

    private SerialConnectionManager mockManager;
    private ActiveDeviceVoltageInjector injector;

    @BeforeEach
    void setUp() {
        mockManager = mock(SerialConnectionManager.class);
        when(mockManager.isConnected()).thenReturn(true);
        injector = new ActiveDeviceVoltageInjector(() -> mockManager);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // injectVoltage()
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("injectVoltage(1, 2.5) sends INJECT_V_CH1:2.500 when connected")
    void injectVoltageSendsFormattedCommandWhenConnected() {
        injector.injectVoltage(1, 2.5);
        verify(mockManager, times(1)).sendLine("INJECT_V_CH1:2.500");
    }
 
    @Test
    @DisplayName("injectVoltage(1, 3.3) sends INJECT_V_CH1:3.300 when connected")
    void injectVoltageChannel1FormatsCorrectly() {
        injector.injectVoltage(1, 3.3);
        verify(mockManager, times(1)).sendLine("INJECT_V_CH1:3.300");
    }
 
    @Test
    @DisplayName("injectVoltage() formats voltage to exactly three decimal places")
    void injectVoltageFormatsVoltageToThreeDecimals() {
        injector.injectVoltage(1, 1.0);
        verify(mockManager, times(1)).sendLine("INJECT_V_CH1:1.000");
    }
 
    @Test
    @DisplayName("injectVoltage() throws IllegalArgumentException for channel < 1")
    void injectVoltageThrowsOnInvalidChannel() {
        assertThrows(IllegalArgumentException.class, () -> injector.injectVoltage(0, 2.5));
    }
 
    @Test
    @DisplayName("injectVoltage() is a no-op when manager supplier returns null")
    void injectVoltageIsNoOpWhenManagerIsNull() {
        ActiveDeviceVoltageInjector nullInjector = new ActiveDeviceVoltageInjector(() -> null);
        assertDoesNotThrow(() -> nullInjector.injectVoltage(1, 2.5));
    }
 
    @Test
    @DisplayName("injectVoltage() is a no-op when manager is not connected")
    void injectVoltageIsNoOpWhenManagerNotConnected() {
        when(mockManager.isConnected()).thenReturn(false);
        injector.injectVoltage(1, 2.5);
        verify(mockManager, never()).sendLine(anyString());
    }
 
    // ──────────────────────────────────────────────────────────────────────────
    // stopInjection()
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("stopInjection(0) sends STOP_INJECT (stops all channels)")
    void stopInjectionChannel0SendsStopInject() {
        injector.stopInjection(0);
        verify(mockManager, times(1)).sendLine("STOP_INJECT");
    }
 
    @Test
    @DisplayName("stopInjection(1) sends STOP_INJECT_CH1")
    void stopInjectionChannel1SendsStopInjectCh1() {
        injector.stopInjection(1);
        verify(mockManager, times(1)).sendLine("STOP_INJECT_CH1");
    }
 
    @Test
    @DisplayName("stopInjection(2) sends STOP_INJECT_CH2")
    void stopInjectionChannel2SendsStopInjectCh2() {
        injector.stopInjection(2);
        verify(mockManager, times(1)).sendLine("STOP_INJECT_CH2");
    }

    @Test
    @DisplayName("stopInjection(channel) throws IllegalArgumentException for channel < 1 except 0")
    void stopInjectionThrowsOnInvalidChannel() {
        assertThrows(IllegalArgumentException.class, () -> injector.stopInjection(-1));
    }
 
    @Test
    @DisplayName("stopInjection() is a no-op when manager supplier returns null")
    void stopInjectionIsNoOpWhenManagerIsNull() {
        ActiveDeviceVoltageInjector nullInjector = new ActiveDeviceVoltageInjector(() -> null);
        assertDoesNotThrow(() -> nullInjector.stopInjection(0));
    }
 
    @Test
    @DisplayName("stopInjection() is a no-op when manager is not connected")
    void stopInjectionIsNoOpWhenManagerNotConnected() {
        when(mockManager.isConnected()).thenReturn(false);
        injector.stopInjection(0);
        verify(mockManager, never()).sendLine(anyString());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Default interface methods
    // ──────────────────────────────────────────────────────────────────────────
 
    @Test
    @DisplayName("injectVoltage(double) default routes to injectVoltage(1, volts)")
    void defaultInjectVoltageRoutesToChannel1() {
        injector.injectVoltage(1.5);
        verify(mockManager, times(1)).sendLine("INJECT_V_CH1:1.500");
    }
 
    @Test
    @DisplayName("stopInjection() default routes to stopInjection(0) --> sends STOP_INJECT")
    void defaultStopInjectionRoutesToChannel0() {
        injector.stopInjection();
        verify(mockManager, times(1)).sendLine("STOP_INJECT");
    }

}
