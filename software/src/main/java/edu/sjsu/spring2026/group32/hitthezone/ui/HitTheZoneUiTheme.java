package edu.sjsu.spring2026.group32.hitthezone.ui;

import java.awt.Color;

/**
 * Shared palette for Hit The Zone UI components.
 */
public final class HitTheZoneUiTheme {
    private static final Color[] PLAYER_COLORS = {
            new Color(30, 120, 220),
            new Color(34, 160, 80),
            new Color(200, 80, 80),
            new Color(160, 80, 200),
    };

    private HitTheZoneUiTheme() {
    }

    public static Color playerColor(int index) {
        return index < PLAYER_COLORS.length ? PLAYER_COLORS[index] : Color.DARK_GRAY;
    }
}
