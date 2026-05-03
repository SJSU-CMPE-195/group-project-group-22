package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PongToolbar Suite")
class PongToolbarTest {
    @Test
    @TODO("Implement selection acceptance and rejection for valid, unavailable, and locked-out variants.")
    void selectionRules_coverPositiveAndNegativeCases() {
        // TODO: verify valid selections fire callbacks and invalid selections revert silently.
        // TODO: include unavailable hardware and locked-out variant cases.
        TodoTestSupport.todo("selectionRules_coverPositiveAndNegativeCases");
    }

    @Test
    @TODO("Implement hardware control visibility, event count, and lock-state behavior.")
    void hardwareControls_updateFromToolbarState() {
        // TODO: verify lock state, event count label updates, and hardware settings button visibility.
        // TODO: include disabled hardware negative cases.
        TodoTestSupport.todo("hardwareControls_updateFromToolbarState");
    }
}
