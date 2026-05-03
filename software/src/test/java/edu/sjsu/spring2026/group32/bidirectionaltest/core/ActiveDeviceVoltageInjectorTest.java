package edu.sjsu.spring2026.group32.bidirectionaltest.core;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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


    // Routing & Multi-Manager Logic

    @Test
    @DisplayName("injectionRouting_matchesExpectedManager: verifies commands reach the correct supplier-provided manager")
    void injectionRouting_matchesExpectedManager() {
        SerialConnectionManager mockA = mock(SerialConnectionManager.class);
        SerialConnectionManager mockB = mock(SerialConnectionManager.class);

        when(mockA.isConnected()).thenReturn(true);
        when(mockB.isConnected()).thenReturn(true);

        final SerialConnectionManager[] activeManager = {mockA};
        ActiveDeviceVoltageInjector dynamicInjector = new ActiveDeviceVoltageInjector(() -> activeManager[0]);

        dynamicInjector.injectVoltage(1, 2.5);

        verify(mockA).isConnected();
        verify(mockA).sendLine("INJECT_V_CH1:2.500");

        verifyNoInteractions(mockB);

        activeManager[0] = mockB;

        dynamicInjector.stopInjection(2);

        verify(mockB).isConnected();
        verify(mockB).sendLine("STOP_INJECT_CH2");
        
        verifyNoMoreInteractions(mockA);
    }

    // Connection State Logic

    @Test
    @DisplayName("injectVoltage() is a no-op when manager is disconnected")
    void injectVoltage_disconnected_isNoOp() {
        when(mockManager.isConnected()).thenReturn(false);
        
        injector.injectVoltage(1, 3.3);
        
        verify(mockManager).isConnected();
        verify(mockManager, never()).sendLine(anyString());
        
    }

    @Test
    @DisplayName("injectVoltage() is a no-op when manager supplier returns null")
    void injectVoltage_nullManager_isNoOp() {
        ActiveDeviceVoltageInjector nullInjector = new ActiveDeviceVoltageInjector(() -> null);
        assertDoesNotThrow(() -> nullInjector.injectVoltage(1, 5.0));

    }

    // Stop Injection Logic

    @Test
    @DisplayName("stopInjection(0) sends STOP_INJECT (global stop)")
    void stopInjection_zero_sendsGlobalStop() {
        injector.stopInjection(0);
        verify(mockManager).sendLine("STOP_INJECT");

    }

    @Test
    @DisplayName("stopInjection(channel) validates channel before sending")
    void stopInjection_validatesChannel() {
        assertThrows(IllegalArgumentException.class, () -> injector.stopInjection(-1));

    }

    // Validation & Edge Cases

    @Test
    @DisplayName("injectVoltage() throws exception for invalid channels")
    void injectVoltage_invalidChannel_throwsException() {
        ActiveDeviceVoltageInjector testInjector = new ActiveDeviceVoltageInjector(() -> null);
        
        assertThrows(IllegalArgumentException.class, () -> testInjector.injectVoltage(0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> testInjector.injectVoltage(-5, 1.0));

    }

    @Test
    @DisplayName("injectVoltage() formats voltage to exactly three decimal places")
    void injectVoltage_formatsToThreeDecimals() {
        injector.injectVoltage(2, 1.2);
        verify(mockManager).sendLine("INJECT_V_CH2:1.200");

    }

}
