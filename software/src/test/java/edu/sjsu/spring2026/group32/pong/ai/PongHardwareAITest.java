package edu.sjsu.spring2026.group32.pong.ai;

import edu.sjsu.spring2026.group32.testsupport.SerialTestRig;
import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PongHardwareAI Suite")
class PongHardwareAITest {
    private SerialTestRig rig;

    @BeforeEach
    void setUp() {
        rig = SerialTestRig.createDualChannelRig();
    }

    @Test
    @TODO("Implement movement decisions from left and right channel spike events.")
    void spikeDrivenMovement_usesDualChannelFirmwareSignals() {
        // TODO: stream realistic dual-channel sample lines into the fake rig.
        // TODO: verify left move, right move, idle, and threshold edge cases.
        TodoTestSupport.todo("spikeDrivenMovement_usesDualChannelFirmwareSignals");
    }

    @Test
    @TODO("Implement injection enable/disable and reconnect behavior.")
    void injectionAndReconnect_coverLifecyclePaths() {
        // TODO: verify injection commands are sent only when enabled and that reconnect restores behavior.
        // TODO: include disconnect negative paths.
        TodoTestSupport.todo("injectionAndReconnect_coverLifecyclePaths");
    }
}
