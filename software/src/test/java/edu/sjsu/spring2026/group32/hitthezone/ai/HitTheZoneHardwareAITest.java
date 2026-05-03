package edu.sjsu.spring2026.group32.hitthezone.ai;

import edu.sjsu.spring2026.group32.testsupport.SerialTestRig;
import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HitTheZoneHardwareAI Suite")
class HitTheZoneHardwareAITest {
    @Test
    @TODO("Implement spike-detection coverage using the serial firmware rig.")
    void scoringSignals_coverPositiveAndNegativeSpikeCases() {
        // TODO: use SerialTestRig to simulate single-channel firmware samples and scoring spikes.
        // TODO: include no-spike, disconnected hardware, and reconnect recovery negative cases.
        TodoTestSupport.todo("scoringSignals_coverPositiveAndNegativeSpikeCases");
    }

    @Test
    @TODO("Implement injection lifecycle coverage for hardware-controlled scoring.")
    void injectionControl_matchesFirmwareProtocol() {
        // TODO: verify injection enable, stop, and cleanup commands are sent to the fake firmware.
        // TODO: include unsupported command or disconnected device negative cases.
        TodoTestSupport.todo("injectionControl_matchesFirmwareProtocol");
    }
}
