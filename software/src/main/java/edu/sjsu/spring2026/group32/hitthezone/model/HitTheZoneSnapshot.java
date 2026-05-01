package edu.sjsu.spring2026.group32.hitthezone.model;

/**
 * Immutable view of the current Hit The Zone match state.
 */
public record HitTheZoneSnapshot(
        int ballX,
        int direction,
        int totalPasses,
        boolean inZone,
        boolean paused,
        long elapsedMs,
        int ballSpeed,
        int zoneWidth,
        int[] hits,
        int[] attempts) {
}
