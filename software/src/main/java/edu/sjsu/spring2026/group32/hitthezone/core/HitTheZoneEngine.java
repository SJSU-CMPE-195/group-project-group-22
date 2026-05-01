package edu.sjsu.spring2026.group32.hitthezone.core;

import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneActionEffect;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneSnapshot;
import edu.sjsu.spring2026.group32.player.model.PlayerType;

import java.util.Arrays;

/**
 * Pure game state and rules for Hit The Zone.
 */
public class HitTheZoneEngine {
    public static final int WIDTH = 820;
    public static final int HEIGHT = 400;
    public static final int BALL_DIAM = 20;
    public static final int START_X = 40;
    public static final int TRACK_Y = 130;
    public static final int DEFAULT_BALL_SPEED = 5;
    public static final int DEFAULT_ZONE_WIDTH = 160;
    private static final int TICK_MS = 16;

    private int[] hits;
    private int[] attempts;
    private boolean[] canScore;
    private HitTheZoneAction[] lastActions;

    private int ballX = START_X;
    private int direction = DEFAULT_BALL_SPEED;
    private int totalPasses;
    private boolean inZone;
    private boolean paused;
    private long elapsedMs;
    private int ballSpeed = DEFAULT_BALL_SPEED;
    private int zoneWidth = DEFAULT_ZONE_WIDTH;

    public HitTheZoneEngine(int playerCount) {
        hits = new int[playerCount];
        attempts = new int[playerCount];
        canScore = new boolean[playerCount];
        lastActions = new HitTheZoneAction[playerCount];
    }

    public void appendPlayer() {
        int size = hits.length + 1;
        hits = Arrays.copyOf(hits, size);
        attempts = Arrays.copyOf(attempts, size);
        canScore = Arrays.copyOf(canScore, size);
        lastActions = Arrays.copyOf(lastActions, size);
    }

    public void tickMotion(int panelWidth) {
        if (paused) {
            return;
        }

        elapsedMs += TICK_MS;
        moveBall(panelWidth);
    }

    public HitTheZoneActionEffect handleAction(int playerIndex,
                                               HitTheZoneAction action,
                                               PlayerType playerType,
                                               boolean hardwarePlayer) {
        if (paused) {
            return HitTheZoneActionEffect.NONE;
        }

        if (action == HitTheZoneAction.SCORE) {
            boolean isHumanScorePress = playerType == PlayerType.HUMAN;
            if (!isHumanScorePress || action != lastActions[playerIndex]) {
                processScore(playerIndex, hardwarePlayer);
            }
        } else if (action != null && action != lastActions[playerIndex]) {
            lastActions[playerIndex] = action;
            if (action == HitTheZoneAction.PAUSE) {
                return HitTheZoneActionEffect.PAUSE_REQUESTED;
            }
            if (action == HitTheZoneAction.RESET) {
                return HitTheZoneActionEffect.RESET_REQUESTED;
            }
        }

        lastActions[playerIndex] = action;
        return HitTheZoneActionEffect.NONE;
    }

    public void togglePause() {
        paused = !paused;
    }

    public void resetMatch() {
        ballX = START_X;
        direction = direction >= 0 ? ballSpeed : -ballSpeed;
        totalPasses = 0;
        inZone = false;
        elapsedMs = 0;
        Arrays.fill(hits, 0);
        Arrays.fill(attempts, 0);
        Arrays.fill(canScore, false);
        Arrays.fill(lastActions, null);
    }

    public void setBallSpeed(int ballSpeed) {
        if (ballSpeed <= 0) {
            throw new IllegalArgumentException("ballSpeed must be > 0");
        }
        boolean movingRight = direction >= 0;
        this.ballSpeed = ballSpeed;
        this.direction = movingRight ? ballSpeed : -ballSpeed;
    }

    public void setZoneWidth(int zoneWidth) {
        if (zoneWidth <= 0 || zoneWidth >= WIDTH) {
            throw new IllegalArgumentException("zoneWidth must be > 0 and < field width");
        }
        this.zoneWidth = zoneWidth;
        int centerX = ballX + BALL_DIAM / 2;
        int zoneStart = zoneStart();
        inZone = centerX >= zoneStart && centerX <= zoneStart + zoneWidth;
    }

    public HitTheZoneSnapshot snapshot() {
        return new HitTheZoneSnapshot(
                ballX,
                direction,
                totalPasses,
                inZone,
                paused,
                elapsedMs,
                ballSpeed,
                zoneWidth,
                Arrays.copyOf(hits, hits.length),
                Arrays.copyOf(attempts, attempts.length));
    }

    public boolean isPaused() {
        return paused;
    }

    public int zoneStart() {
        return (WIDTH - zoneWidth) / 2;
    }

    public int getPlayerCount() {
        return hits.length;
    }

    private void moveBall(int panelWidth) {
        ballX += direction;

        int width = panelWidth > 0 ? panelWidth : WIDTH;
        int rightBound = width - BALL_DIAM;
        int zoneStart = zoneStart();

        if (ballX <= 0) {
            ballX = 0;
            direction = ballSpeed;
        } else if (ballX >= rightBound) {
            ballX = rightBound;
            direction = -ballSpeed;
        }

        int centerX = ballX + BALL_DIAM / 2;
        boolean nowInZone = centerX >= zoneStart && centerX <= zoneStart + zoneWidth;

        if (!inZone && nowInZone) {
            totalPasses++;
            for (int i = 0; i < canScore.length; i++) {
                canScore[i] = true;
                lastActions[i] = null;
            }
        } else if (inZone && !nowInZone) {
            Arrays.fill(canScore, false);
        }

        inZone = nowInZone;
    }

    private void processScore(int playerIndex, boolean hardwarePlayer) {
        attempts[playerIndex]++;

        int centerX = ballX + BALL_DIAM / 2;
        int zoneStart = zoneStart();
        boolean inside = centerX >= zoneStart && centerX <= zoneStart + zoneWidth;
        boolean shouldCredit = canScore[playerIndex] && (inside || hardwarePlayer);

        if (shouldCredit) {
            hits[playerIndex]++;
        }
    }
}
