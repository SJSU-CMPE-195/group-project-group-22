package edu.sjsu.spring2026.group32.pong.core;

import edu.sjsu.spring2026.group32.pong.model.PongAction;
import edu.sjsu.spring2026.group32.pong.model.PongGameState;
import edu.sjsu.spring2026.group32.pong.model.PongSnapshot;
import edu.sjsu.spring2026.group32.pong.model.PongTickResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
@DisplayName("PongEngine Suite")
class PongEngineTest {

    @Test
    void lifecycleStateTransitions_workCorrectly() {
        PongEngine engine = new PongEngine();

        assertEquals(PongGameState.PAUSED, engine.getGameState());

        long now = System.currentTimeMillis();

        engine.startCountdown(now);
        assertEquals(PongGameState.COUNTDOWN, engine.getGameState());

        engine.tickCountdown(now + PongEngine.COUNTDOWN_MS - 1);
        assertEquals(PongGameState.COUNTDOWN, engine.getGameState());

        engine.tickCountdown(now + PongEngine.COUNTDOWN_MS);
        assertEquals(PongGameState.PLAYING, engine.getGameState());

        engine.pause();
        assertEquals(PongGameState.PAUSED, engine.getGameState());

        engine.pause();
        assertEquals(PongGameState.PAUSED, engine.getGameState());

        engine.resetMatch();
        assertEquals(PongGameState.PAUSED, engine.getGameState());

        PongSnapshot snap = engine.snapshot();
        assertEquals(0, snap.topScore());
        assertEquals(0, snap.bottomScore());

    }

    @Test
    void motionAndBounds_behaveAsExpected() {
        PongEngine engine = new PongEngine();

        PongSnapshot before = engine.snapshot();

        engine.tickPlaying(PongAction.LEFT, PongAction.LEFT, false, false);
        PongSnapshot afterLeft = engine.snapshot();

        assertTrue(afterLeft.topPaddleX() < before.topPaddleX());
        assertTrue(afterLeft.bottomPaddleX() < before.bottomPaddleX());

        engine.tickPlaying(PongAction.RIGHT, PongAction.RIGHT, false, false);
        PongSnapshot afterRight = engine.snapshot();

        assertTrue(afterRight.topPaddleX() > afterLeft.topPaddleX());
        assertTrue(afterRight.bottomPaddleX() > afterLeft.bottomPaddleX());

        for (int i = 0; i < 200; i++) {
            engine.tickPlaying(PongAction.LEFT, PongAction.LEFT, false, false);
        }

        PongSnapshot clampedLeft = engine.snapshot();
        assertEquals(0, clampedLeft.topPaddleX());
        assertEquals(0, clampedLeft.bottomPaddleX());

        for (int i = 0; i < 200; i++) {
            engine.tickPlaying(PongAction.RIGHT, PongAction.RIGHT, false, false);
        }

        PongSnapshot clampedRight = engine.snapshot();
        assertEquals(PongEngine.FIELD_WIDTH - PongEngine.PADDLE_WIDTH, clampedRight.topPaddleX());
        assertEquals(PongEngine.FIELD_WIDTH - PongEngine.PADDLE_WIDTH, clampedRight.bottomPaddleX());

        PongSnapshot snap = engine.snapshot();

        setBall(engine, 0, snap.ballY(), -5, 0);

        engine.tickPlaying(PongAction.IDLE, PongAction.IDLE, false, false);
        PongSnapshot bounced = engine.snapshot();

        assertTrue(bounced.ballVelX() > 0); // bounced right
    }

    @Test
    void collisionsAndScoring_Paths() {
        PongEngine engine = new PongEngine();

        setBall(engine, 300,PongEngine.TOP_PADDLE_Y + 5, 0, -5);

        setTopPaddle(engine, 260);

        PongTickResult resultTop = engine.tickPlaying(
            PongAction.IDLE,
            PongAction.IDLE,
            false,
            false
        );

        assertEquals(PongTickResult.RALLY_RESET, resultTop);
        assertTrue(engine.snapshot().ballVelY() > 0); // bounced downward

        setBall(engine, 300, PongEngine.BOTTOM_PADDLE_Y - 5, 0, 5); 
        setBottomPaddle(engine, 260);

        PongTickResult resultBottom = engine.tickPlaying(
            PongAction.IDLE,
            PongAction.IDLE,
            false,
            false
        );

        assertEquals(PongTickResult.RALLY_RESET, resultBottom);
        assertTrue(engine.snapshot().ballVelY() < 0); // bounced upward

        setBall(engine, 100, -20, 0, -5);

        PongTickResult scoreBottom = engine.tickPlaying(
            PongAction.IDLE,
            PongAction.IDLE,
            false,
            false
        );

        assertEquals(PongTickResult.BOTTOM_SCORED, scoreBottom);
        assertEquals(1, engine.snapshot().bottomScore());

        setBall(engine, 100, PongEngine.FIELD_HEIGHT + 20, 0, 5);

        PongTickResult scoreTop = engine.tickPlaying(
            PongAction.IDLE,
            PongAction.IDLE,
            false,
            false
        );

        assertEquals(PongTickResult.TOP_SCORED, scoreTop);
        assertEquals(1, engine.snapshot().topScore());

        setBall(engine, 100, 100, 0, 0);

        PongTickResult none = engine.tickPlaying(
            PongAction.IDLE,
            PongAction.IDLE,
            false,
            false
        );

        assertEquals(PongTickResult.NONE, none);
    }

    // helper functions

    private void setBall(PongEngine engine, int x, int y, int vx, int vy) {
        setField(engine, "ballX", x);
        setField(engine, "ballY", y);
        setField(engine, "ballVelX", vx);
        setField(engine, "ballVelY", vy);
    }

    private void setTopPaddle(PongEngine engine, int x) {
        setField(engine, "topPaddleX", x);
    }

    private void setBottomPaddle(PongEngine engine, int x) {
        setField(engine, "bottomPaddleX", x);
    }

    private void setField(PongEngine engine, String name, int value) {
        try {
            var field = PongEngine.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(engine, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}