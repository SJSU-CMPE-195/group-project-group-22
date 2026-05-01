package edu.sjsu.spring2026.group32.pong.core;

import edu.sjsu.spring2026.group32.hardware.NeuralHardwareConfig;
import edu.sjsu.spring2026.group32.pong.model.*;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure game rules and mutable match state for Pong.
 */
public class PongEngine {
    public static final int FIELD_WIDTH = 600;
    public static final int FIELD_HEIGHT = 460;
    public static final int PADDLE_WIDTH = 80;
    public static final int PADDLE_HEIGHT = 12;
    public static final int BALL_SIZE = 12;
    public static final int PADDLE_MARGIN = 28;
    public static final int TOP_PADDLE_Y = PADDLE_MARGIN;
    public static final int BOTTOM_PADDLE_Y = FIELD_HEIGHT - PADDLE_MARGIN - PADDLE_HEIGHT;
    public static final long COUNTDOWN_MS = 2_000L;

    private static final int PADDLE_SPEED = 7;
    private static final int[] SPEED_VEL_X = {3, 4, 5, 6, 7};
    private static final int[] SPEED_VEL_Y = {3, 5, 7, 9, 11};

    private volatile PongGameState gameState = PongGameState.PAUSED;
    private long countdownStartMs;

    private int topPaddleX;
    private int bottomPaddleX;
    private int ballX;
    private int ballY;
    private int ballVelX;
    private int ballVelY;
    private int topScore;
    private int bottomScore;
    private int ballSpeedLevel;
    private boolean constantSpeed = true;

    public PongEngine() {
        resetBall();
    }

    public PongState topPlayerState() {
        return new PongState(topPaddleX, ballX, ballY, FIELD_WIDTH, ballVelY);
    }

    public PongState bottomPlayerState() {
        return new PongState(bottomPaddleX, ballX, ballY, FIELD_WIDTH, ballVelY);
    }

    public void tickCountdown(long now) {
        if (now - countdownStartMs >= COUNTDOWN_MS) {
            gameState = PongGameState.PLAYING;
        }
    }

    public PongTickResult tickPlaying(PongAction topAction,
                                      PongAction bottomAction,
                                      boolean topHardware,
                                      boolean bottomHardware) {
        if (ballVelX == 0 && ballVelY == 0) {
            return PongTickResult.NONE;
        }

        topPaddleX = clampPaddle(topPaddleX + dx(topAction, topHardware));
        bottomPaddleX = clampPaddle(bottomPaddleX + dx(bottomAction, bottomHardware));

        ballX += ballVelX;
        ballY += ballVelY;

        if (ballX <= 0) {
            ballX = 0;
            ballVelX = Math.abs(ballVelX);
        } else if (ballX + BALL_SIZE >= FIELD_WIDTH) {
            ballX = FIELD_WIDTH - BALL_SIZE;
            ballVelX = -Math.abs(ballVelX);
        }

        if (ballVelY < 0
                && ballY <= TOP_PADDLE_Y + PADDLE_HEIGHT
                && ballY + BALL_SIZE >= TOP_PADDLE_Y
                && ballX + BALL_SIZE >= topPaddleX
                && ballX <= topPaddleX + PADDLE_WIDTH) {
            ballVelY = Math.abs(ballVelY);
            ballVelX += deflect(ballX, topPaddleX);
            maybeNormalizeSpeed();
            ballY = TOP_PADDLE_Y + PADDLE_HEIGHT + 1;
            return PongTickResult.RALLY_RESET;
        }

        if (ballVelY > 0
                && ballY + BALL_SIZE >= BOTTOM_PADDLE_Y
                && ballY <= BOTTOM_PADDLE_Y + PADDLE_HEIGHT
                && ballX + BALL_SIZE >= bottomPaddleX
                && ballX <= bottomPaddleX + PADDLE_WIDTH) {
            ballVelY = -Math.abs(ballVelY);
            ballVelX += deflect(ballX, bottomPaddleX);
            maybeNormalizeSpeed();
            ballY = BOTTOM_PADDLE_Y - BALL_SIZE - 1;
            return PongTickResult.RALLY_RESET;
        }

        if (ballY + BALL_SIZE < 0) {
            bottomScore++;
            return PongTickResult.BOTTOM_SCORED;
        }

        if (ballY > FIELD_HEIGHT) {
            topScore++;
            return PongTickResult.TOP_SCORED;
        }

        return PongTickResult.NONE;
    }

    public void pause() {
        gameState = PongGameState.PAUSED;
    }

    public void startCountdown(long now) {
        gameState = PongGameState.COUNTDOWN;
        countdownStartMs = now;
        resetBall();
    }

    public void resetMatch() {
        topScore = 0;
        bottomScore = 0;
        gameState = PongGameState.PAUSED;
        resetBall();
    }

    public void resetBall() {
        ballX = FIELD_WIDTH / 2 - BALL_SIZE / 2;
        ballY = FIELD_HEIGHT / 2 - BALL_SIZE / 2;
        ballVelX = (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) * SPEED_VEL_X[ballSpeedLevel];
        ballVelY = SPEED_VEL_Y[ballSpeedLevel];
        topPaddleX = FIELD_WIDTH / 2 - PADDLE_WIDTH / 2;
        bottomPaddleX = FIELD_WIDTH / 2 - PADDLE_WIDTH / 2;
    }

    public PongSnapshot snapshot() {
        return new PongSnapshot(
                topPaddleX,
                bottomPaddleX,
                ballX,
                ballY,
                ballVelX,
                ballVelY,
                topScore,
                bottomScore,
                ballSpeedLevel,
                constantSpeed,
                gameState,
                countdownStartMs);
    }

    public void setBallSpeedLevel(int level) {
        if (level < 0 || level >= SPEED_VEL_X.length) {
            return;
        }
        ballSpeedLevel = level;
    }

    public void toggleConstantSpeed() {
        constantSpeed = !constantSpeed;
    }

    public PongGameState getGameState() {
        return gameState;
    }

    private void maybeNormalizeSpeed() {
        if (!constantSpeed) {
            return;
        }
        double target = Math.sqrt(
                (double) SPEED_VEL_X[ballSpeedLevel] * SPEED_VEL_X[ballSpeedLevel]
                        + (double) SPEED_VEL_Y[ballSpeedLevel] * SPEED_VEL_Y[ballSpeedLevel]);
        double current = Math.sqrt((double) ballVelX * ballVelX + (double) ballVelY * ballVelY);
        if (current == 0) {
            return;
        }
        double scale = target / current;
        ballVelX = (int) Math.round(ballVelX * scale);
        ballVelY = (int) Math.round(ballVelY * scale);
        if (ballVelY == 0) {
            ballVelY = 1;
        }
    }

    private static int dx(PongAction action, boolean isHardware) {
        int speed = isHardware ? NeuralHardwareConfig.PONG_HARDWARE_PADDLE_SPEED : PADDLE_SPEED;
        return switch (action) {
            case LEFT -> -speed;
            case RIGHT -> speed;
            case IDLE -> 0;
        };
    }

    private static int clampPaddle(int x) {
        return Math.max(0, Math.min(FIELD_WIDTH - PADDLE_WIDTH, x));
    }

    private static int deflect(int ballX, int paddleX) {
        int off = (ballX + BALL_SIZE / 2) - (paddleX + PADDLE_WIDTH / 2);
        return Math.max(-3, Math.min(3, off / 10));
    }
}
