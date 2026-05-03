package edu.sjsu.spring2026.group32.pong.core;

import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PongScoreboardService Suite")
class PongScoreboardServiceTest {
    @Test
    @TODO("Implement canonical matchup creation and self-matchup ignore behavior.")
    void recordPoint_createsCanonicalRecords() {
        // TODO: verify A-vs-B and B-vs-A map to one record and self-matchups are ignored.
        // TODO: assert points are attributed to the correct canonical side.
        TodoTestSupport.todo("recordPoint_createsCanonicalRecords");
    }

    @Test
    @TODO("Implement persistence, malformed file handling, reset, and immutability behavior.")
    void persistenceAndReset_coverPositiveAndNegativePaths() {
        // TODO: verify load/save, malformed-line skipping, reset persistence, and unmodifiable record views.
        // TODO: include missing-file and malformed-number negative cases.
        TodoTestSupport.todo("persistenceAndReset_coverPositiveAndNegativePaths");
    }
}
