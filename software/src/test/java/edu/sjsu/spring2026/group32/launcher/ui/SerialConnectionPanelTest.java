package edu.sjsu.spring2026.group32.launcher.ui;

import edu.sjsu.spring2026.group32.testsupport.SerialTestRig;
import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SerialConnectionPanel Suite")
class SerialConnectionPanelTest {
    private SerialTestRig rig;

    @BeforeEach
    void setUp() {
        rig = SerialTestRig.createSingleChannelRig();
    }

    @Test
    @TODO("Implement refresh list, unsupported port, and empty-port positive/negative UI cases.")
    void refreshAndSelectionWorkflow_coverPortDiscoveryCases() {
        // TODO: verify visible port refresh behavior, unsupported-device warnings, and empty-port messaging.
        // TODO: include filtered and excluded port cases.
        TodoTestSupport.todo("refreshAndSelectionWorkflow_coverPortDiscoveryCases");
    }

    @Test
    @TODO("Implement successful connect, wrong-channel rejection, and disconnect/reconnect UI behavior.")
    void connectAndDisconnect_coverHardwareLifecycleCases() {
        // TODO: connect against fake firmware, assert status updates, and simulate disconnect/reconnect.
        // TODO: include wrong-channel and failed-open negative paths.
        TodoTestSupport.todo("connectAndDisconnect_coverHardwareLifecycleCases");
    }
}
