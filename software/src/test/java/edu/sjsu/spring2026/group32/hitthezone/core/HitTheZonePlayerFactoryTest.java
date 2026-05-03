package edu.sjsu.spring2026.group32.hitthezone.core;

import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HitTheZonePlayerFactory Suite")
class HitTheZonePlayerFactoryTest {
    @Test
    @TODO("Implement default-player creation coverage with and without hardware.")
    void defaultPlayerCreation_coversPositiveAndNegativeCases() {
        // TODO: verify default player lists for connected and disconnected hardware managers.
        // TODO: include order, player type, and missing-hardware negative cases.
        TodoTestSupport.todo("defaultPlayerCreation_coversPositiveAndNegativeCases");
    }

    @Test
    @TODO("Implement standalone-player creation coverage for launcher-independent startup.")
    void standalonePlayerCreation_buildsExpectedRoster() {
        // TODO: verify standalone rosters with and without an injected hardware player.
        // TODO: include null hardware player negative cases.
        TodoTestSupport.todo("standalonePlayerCreation_buildsExpectedRoster");
    }
}
