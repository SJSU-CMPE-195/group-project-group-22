package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scoreboard Suite")
class ScoreboardTest {
    @Test
    @TODO("Implement UI dialog population tests based on service-backed records.")
    void createPopupDialog_reflectsCurrentScoreboardData() {
        // TODO: seed scoreboard data and assert dialog title, columns, and rows.
        // TODO: include empty-scoreboard negative case.
        TodoTestSupport.todo("createPopupDialog_reflectsCurrentScoreboardData");
    }

    @Test
    @TODO("Implement reset button behavior after user confirmation.")
    void resetButton_clearsVisibleRowsAndBackingData() {
        // TODO: simulate user confirmation and assert rows clear and backing service resets.
        // TODO: include cancel/no-op negative case.
        TodoTestSupport.todo("resetButton_clearsVisibleRowsAndBackingData");
    }
}
