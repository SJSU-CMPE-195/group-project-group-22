package edu.sjsu.spring2026.group32.pong.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.sjsu.spring2026.group32.pong.core.PongEngine;
import edu.sjsu.spring2026.group32.pong.model.PongAction;
import edu.sjsu.spring2026.group32.pong.model.PongState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DisplayName("PongSoftwareAI Suite")
class PongSoftwareAITest {

    // paddleX = 300 --> paddleCenter = 300 + 40 = 340
    private static final int PADDLE_X      = 300;
    private static final int PADDLE_CENTER = PADDLE_X + PongEngine.PADDLE_WIDTH / 2; // 340
    private static final int FIELD_WIDTH   = PongEngine.FIELD_WIDTH;


    @Test
    @DisplayName("hard AI moves LEFT when ball is left of dead zone")
    void aiDecisionMaking_hardAI_movesLeft() {
        PongSoftwareAI ai = hardAI();
        // diff = 100 - 340 = -240, well outside deadZone=8
        assertEquals(PongAction.LEFT, ai.getNextMove(state(100)), "hard AI should move LEFT when ball is far left of paddle center");
    }

    @Test
    @DisplayName("hard AI moves RIGHT when ball is right of dead zone")
    void aiDecisionMaking_hardAI_movesRight() {
        PongSoftwareAI ai = hardAI();
        // diff = 500 - 340 = 160, well outside deadZone=8
        assertEquals(PongAction.RIGHT, ai.getNextMove(state(500)), "hard AI should move RIGHT when ball is far right of paddle center");
    }

    @Test
    @DisplayName("hard AI returns IDLE when ball is within dead zone")
    void aiDecisionMaking_hardAI_idleWithinDeadZone() {
        PongSoftwareAI ai = hardAI();
        // diff = 340 - 340 = 0 
        assertEquals(PongAction.IDLE, ai.getNextMove(state(PADDLE_CENTER)), "hard AI should be IDLE when ball is exactly at paddle center");

        // diff = 347 - 340 = 7 
        assertEquals(PongAction.IDLE, ai.getNextMove(state(PADDLE_CENTER + 7)), "hard AI should be IDLE when ball is within dead zone (right side)");

        // diff = 333 - 340 = -7
        assertEquals(PongAction.IDLE, ai.getNextMove(state(PADDLE_CENTER - 7)), "hard AI should be IDLE when ball is within dead zone (left side)");
    }

    @Test
    @DisplayName("hard AI acts exactly at dead zone boundary")
    void aiDecisionMaking_hardAI_deadZoneBoundary() {
        PongSoftwareAI ai = hardAI();
        // diff = +8, exactly at boundary, not outside (> not >=), so still IDLE
        assertEquals(PongAction.IDLE, ai.getNextMove(state(PADDLE_CENTER + 8)), "diff == deadZone should still be IDLE (boundary is exclusive)");

        // diff = +9, one pixel outside --> RIGHT
        assertEquals(PongAction.RIGHT, ai.getNextMove(state(PADDLE_CENTER + 9)), "diff just outside dead zone should produce RIGHT");

        // diff = -9, one pixel outside --> LEFT
        assertEquals(PongAction.LEFT, ai.getNextMove(state(PADDLE_CENTER - 9)), "diff just outside dead zone should produce LEFT");
    }

    // easy AI (deadZone=22, prob=0.72)

    @Test
    @DisplayName("easy AI returns IDLE for positions hard AI would act on, within its wider dead zone")
    void aiDecisionMaking_easyAI_widerDeadZone() {
        // prob=1.0 here to isolate dead zone behavior from the RNG
        PongSoftwareAI ai = new PongSoftwareAI("Easy-det", 22, 1.0);

        // diff = +9 --> hard AI fires RIGHT, easy AI is still in dead zone
        assertEquals(PongAction.IDLE, ai.getNextMove(state(PADDLE_CENTER + 9)), "easy AI dead zone=22 should be IDLE where hard AI dead zone=8 would fire");

        // diff = +23 --> outside easy dead zone --> RIGHT
        assertEquals(PongAction.RIGHT, ai.getNextMove(state(PADDLE_CENTER + 23)), "easy AI should fire RIGHT just outside its wider dead zone");

        // diff = -23 --> outside easy dead zone --> LEFT
        assertEquals(PongAction.LEFT, ai.getNextMove(state(PADDLE_CENTER - 23)), "easy AI should fire LEFT just outside its wider dead zone");
    }

    @Test
    @DisplayName("probability=0.0 always returns IDLE regardless of ball position")
    void aiDecisionMaking_probabilityZero_alwaysIdle() {
        PongSoftwareAI ai = new PongSoftwareAI("NeverActs", 0, 0.0);
        // ball far left, would be LEFT if prob allowed it
        assertEquals(PongAction.IDLE, ai.getNextMove(state(100)), "prob=0.0 should always skip and return IDLE");
        assertEquals(PongAction.IDLE, ai.getNextMove(state(500)), "prob=0.0 should always skip and return IDLE");
    }

    @Test
    @DisplayName("probability=1.0 never skips — always acts on ball position")
    void aiDecisionMaking_probabilityOne_neverSkips() {
        PongSoftwareAI ai = new PongSoftwareAI("AlwaysActs", 0, 1.0);
        // deadZone=0: any non-zero diff should produce a directional action
        assertNotEquals(PongAction.IDLE, ai.getNextMove(state(100)), "prob=1.0 with deadZone=0 should never return IDLE for off-center ball");
        assertNotEquals(PongAction.IDLE, ai.getNextMove(state(500)), "prob=1.0 with deadZone=0 should never return IDLE for off-center ball");
    }

    // helpers

    private static PongSoftwareAI hardAI() {
        return new PongSoftwareAI("Hard", 8, 1.0);
    }

    /** Builds a PongState with the given ballX and a fixed paddleX and incoming ball. */
    private static PongState state(int ballX) {
        return new PongState(PADDLE_X, ballX, 100, FIELD_WIDTH, -3);
    }
    
}