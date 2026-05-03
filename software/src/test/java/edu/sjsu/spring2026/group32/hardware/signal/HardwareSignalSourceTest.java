package edu.sjsu.spring2026.group32.hardware.signal;

import edu.sjsu.spring2026.group32.testsupport.SerialTestRig;
import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HardwareSignalSource Suite")
class HardwareSignalSourceTest {
    private SerialTestRig rig;

    @BeforeEach
    void setUp() {
        rig = SerialTestRig.createSingleChannelRig();
    }

    @Test
    @TODO("Implement voltage update tests from realistic firmware sample frames.")
    void voltageUpdates_followFirmwareSamples() {
        // TODO: connect the fake device, stream sample lines, and assert latest voltage updates.
        // TODO: cover disconnected and connected states.
        TodoTestSupport.todo("voltageUpdates_followFirmwareSamples");
    }

    @Test
    @TODO("Implement spike detection behavior using realistic sample sequences.")
    void hasSpike_delegatesOnlyWhenConnected() {
        // TODO: verify false when disconnected and positive/negative spike detection when connected.
        // TODO: include threshold edge cases.
        TodoTestSupport.todo("hasSpike_delegatesOnlyWhenConnected");
    }

    @Test
    @TODO("Implement command formatting tests for injection and stop operations.")
    void injectionCommands_matchFirmwareProtocol() {
        // TODO: call injectVoltage and stopInjection across valid and invalid channels.
        // TODO: assert outgoing commands captured by the fake firmware and invalid-channel exceptions.
        TodoTestSupport.todo("injectionCommands_matchFirmwareProtocol");
    }
}
