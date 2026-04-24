package edu.sjsu.spring2026.group32.bidirectionaltest;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
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
    @DisplayName("[TODO] injectVoltage() sends correctly formatted INJECT_V_CHx command when connected")
    void injectVoltageSendsFormattedCommandWhenConnected() {
        // TODO: implement
        // call injector.injectVoltage(0, 2.5) and verify mockManager.sendLine("INJECT_V_CH0:2.500")
    }

    @Test
    @DisplayName("[TODO] injectVoltage() is a no-op when the manager supplier returns null")
    void injectVoltageIsNoOpWhenManagerIsNull() {
        // TODO: implement
        // create injector with () -> null, call injectVoltage(), verify no exception and no sendLine call
    }

    @Test
    @DisplayName("[TODO] injectVoltage() is a no-op when the manager is not connected")
    void injectVoltageIsNoOpWhenManagerNotConnected() {
        // TODO: implement
        // set mockManager.isConnected() to return false, call injectVoltage(), verify no sendLine
    }

    @Test
    @DisplayName("[TODO] injectVoltage() sends INJECT_V_CH0 for channel 0 with three decimal places")
    void injectVoltageChannel0FormatsCorrectly() {
        // TODO: implement
        // verify format string: "INJECT_V_CH0:%.3f" applied to the given voltage
    }

    @Test
    @DisplayName("[TODO] injectVoltage() sends INJECT_V_CH1 for channel 1")
    void injectVoltageChannel1FormatsCorrectly() {
        // TODO: implement
        // call injector.injectVoltage(1, 3.3) and verify sendLine("INJECT_V_CH1:3.300")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // stopInjection()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[TODO] stopInjection(0) sends STOP_INJECT to the manager")
    void stopInjectionChannel0SendsStopInject() {
        // TODO: implement
        // call injector.stopInjection(0) and verify sendLine("STOP_INJECT")
    }

    @Test
    @DisplayName("[TODO] stopInjection(1) sends STOP_INJECT_CH1 to the manager")
    void stopInjectionChannel1SendsStopInjectCh1() {
        // TODO: implement
        // call injector.stopInjection(1) and verify sendLine("STOP_INJECT_CH1")
    }

    @Test
    @DisplayName("[TODO] stopInjection() is a no-op when the manager supplier returns null")
    void stopInjectionIsNoOpWhenManagerIsNull() {
        // TODO: implement
        // create injector with () -> null, call stopInjection(0), verify no exception
    }

    @Test
    @DisplayName("[TODO] stopInjection() is a no-op when the manager is not connected")
    void stopInjectionIsNoOpWhenManagerNotConnected() {
        // TODO: implement
        // set mockManager.isConnected() to return false, verify no sendLine call
    }
}
