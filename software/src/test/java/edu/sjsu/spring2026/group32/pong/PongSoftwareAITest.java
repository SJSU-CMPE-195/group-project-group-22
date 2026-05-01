package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.model.PlayerType;
import edu.sjsu.spring2026.group32.pong.ai.PongSoftwareAI;
import edu.sjsu.spring2026.group32.pong.model.PongAction;
import edu.sjsu.spring2026.group32.pong.model.PongState;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PongSoftwareAI}.
 *
 * <p>PADDLE_WIDTH is 80 px (matches the constant in PongSoftwareAI and PongGame).
 * paddleCenter = paddleX + 40.
 * diff = ballX – paddleCenter.
 * w/ reactionProbability = 1.0 the RNG skip is never triggered, making all direction tests fully deterministic.
 * 
 */
@DisplayName("PongSoftwareAI Suite")
class PongSoftwareAITest {

    // matches w/ PongSoftwareAI.PADDLE_WIDTH
    private static final int PADDLE_WIDTH = 80;

    // ──────────────────────────────────────────────────────────────────────────
    // Direction mapping (reactionProbability=1.0 --> deterministic)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns LEFT when ball is left of paddle center beyond dead zone")
    void returnsLeftWhenBallFarLeft() {
        // paddleX = 100 --> center = 140; deadZone = 10; ball at 100 --> diff = -40 < -10 --> LEFT
        PongSoftwareAI ai = new PongSoftwareAI("AI", 10, 1.0);
        PongState state = new PongState(100, 100, 300, 600);
        assertEquals(PongAction.LEFT, ai.getNextMove(state));
    }

    @Test
    @DisplayName("Returns RIGHT when ball is right of paddle center beyond dead zone")
    void returnsRightWhenBallFarRight() {
        // paddleX = 100 --> center = 140; deadZone = 10; ball at 200 --> diff = 60 > 10 --> RIGHT
        PongSoftwareAI ai = new PongSoftwareAI("AI", 10, 1.0);
        PongState state = new PongState(100, 200, 300, 600);
        assertEquals(PongAction.RIGHT, ai.getNextMove(state));
    }

    @Test
    @DisplayName("Returns IDLE when ball is within the dead zone")
    void returnsIdleWhenBallInsideDeadZone() {
        // paddleX = 100 --> center = 140; deadZone = 10; ball at 145 --> diff = 5, |5|<= 10 --> IDLE
        PongSoftwareAI ai = new PongSoftwareAI("AI", 10, 1.0);
        PongState state = new PongState(100, 145, 300, 600);
        assertEquals(PongAction.IDLE, ai.getNextMove(state));
    }

    @Test
    @DisplayName("Returns IDLE when ball is exactly at the left dead-zone boundary")
    void returnsIdleAtLeftDeadZoneBoundary() {
        // paddleX = 100 --> center = 140; deadZone = 10; ball at 130 --> diff = -10, not < -10 --> IDLE
        PongSoftwareAI ai = new PongSoftwareAI("AI", 10, 1.0);
        PongState state = new PongState(100, 130, 300, 600);
        assertEquals(PongAction.IDLE, ai.getNextMove(state));
    }

    @Test
    @DisplayName("Returns IDLE when ball is exactly at the right dead-zone boundary")
    void returnsIdleAtRightDeadZoneBoundary() {
        // paddleX=100 --> center = 140; deadZone = 10; ball at 150 --> diff = 10, not > 10 --> IDLE
        PongSoftwareAI ai = new PongSoftwareAI("AI", 10, 1.0);
        PongState state = new PongState(100, 150, 300, 600);
        assertEquals(PongAction.IDLE, ai.getNextMove(state));
    }

    @Test
    @DisplayName("Returns LEFT when ball is one pixel outside the left boundary")
    void returnsLeftJustOutsideLeftBoundary() {
        // paddleX=100 --> center=140; deadZone=10; ball at 129 --> diff = -11 < -10 --> LEFT
        PongSoftwareAI ai = new PongSoftwareAI("AI", 10, 1.0);
        PongState state = new PongState(100, 129, 300, 600);
        assertEquals(PongAction.LEFT, ai.getNextMove(state));
    }

    @Test
    @DisplayName("Returns RIGHT when ball is one pixel outside the right boundary")
    void returnsRightJustOutsideRightBoundary() {
        // paddleX=100 --> center=140; deadZone=10; ball at 151 --> diff=11 > 10 --> RIGHT
        PongSoftwareAI ai = new PongSoftwareAI("AI", 10, 1.0);
        PongState state = new PongState(100, 151, 300, 600);
        assertEquals(PongAction.RIGHT, ai.getNextMove(state));
    }

    @Test
    @DisplayName("Returns IDLE when reactionProbability is 0.0 regardless of ball position")
    void zeroProbabilityAlwaysIdle() {

        PongSoftwareAI ai = new PongSoftwareAI("AI", 0, 0.0);
    
        PongState state = new PongState(0, 500, 300, 600);

        // run a lot of times to confirm it is never anything other than IDLE
        for (int i = 0; i < 50; i++) {
            assertEquals(PongAction.IDLE, ai.getNextMove(state), "reactionProbability=0.0 should always return IDLE");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Dead-zone = 0 (any difference triggers movement)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Zero dead zone: LEFT when ball is even 1px left of center")
    void zeroDeadZone_leftWhenBallSlightlyLeft() {
        // paddleX=100 --> center=140; deadZone=0; ball at 139 --> diff=-1 < 0 --> LEFT
        PongSoftwareAI ai = new PongSoftwareAI("AI", 0, 1.0);
        PongState state = new PongState(100, 139, 300, 600);
        assertEquals(PongAction.LEFT, ai.getNextMove(state));
    }

    @Test
    @DisplayName("Zero dead zone: IDLE when ball is exactly at paddle center")
    void zeroDeadZone_idleWhenBallAtCenter() {
        // paddleX = 100 --> center = 140; deadZone = 0; ball at 140 --> diff=0 --> IDLE
        PongSoftwareAI ai = new PongSoftwareAI("AI", 0, 1.0);

        PongState state = new PongState(100, 140, 300, 600);

        assertEquals(PongAction.IDLE, ai.getNextMove(state));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Parameter clamping
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Negative reactionDeadZone is clamped to 0")
    void negativeDeadZoneClampedToZero() {
        // Should behave identically to deadZone=0
        PongSoftwareAI ai = new PongSoftwareAI("AI", -50, 1.0);

        // ball exactly at center --> IDLE
        PongState center = new PongState(100, 140, 300, 600);

        assertEquals(PongAction.IDLE, ai.getNextMove(center));

        // ball 1px left --> LEFT
        PongState left = new PongState(100, 139, 300, 600);

        assertEquals(PongAction.LEFT, ai.getNextMove(left));
    }

    @Test
    @DisplayName("reactionProbability above 1.0 is clamped to 1.0 (always reacts)")
    void probabilityAboveOneClampedToOne() {
        PongSoftwareAI ai = new PongSoftwareAI("AI", 0, 999.9);
        PongState farLeft = new PongState(0, 0, 300, 600);
        // With prob clamped to 1.0 it should never be IDLE when ball is far left
        for (int i = 0; i < 20; i++) {
            assertEquals(PongAction.LEFT, ai.getNextMove(farLeft), "prob>1.0 clamped to 1.0 — should always react");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Metadata
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getName() returns the name passed to the constructor")
    void getNameReturnsConstructorValue() {
        PongSoftwareAI ai = new PongSoftwareAI("EasyBot", 10, 0.72);
        assertEquals("EasyBot", ai.getName());
    }

    @Test
    @DisplayName("getType() returns PlayerType.SOFTWARE")
    void getTypeReturnsSoftware() {
        PongSoftwareAI ai = new PongSoftwareAI("AI", 10, 1.0);
        assertEquals(PlayerType.SOFTWARE, ai.getType());
    }
}
