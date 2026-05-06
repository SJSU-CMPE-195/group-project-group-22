package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.pong.core.PongEngine;
import edu.sjsu.spring2026.group32.pong.model.PongSnapshot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.function.IntConsumer;

/**
 * Renders and hit-tests Pong's pause overlay.
 */
public class PongPauseOverlay {
    private static final String[] SPEED_LABELS = {"Slow", "Normal", "High"};

    private final Rectangle[] speedReacts = new Rectangle[SPEED_LABELS.length];
    private Rectangle checkboxRect;
    private Rectangle resumeRect;
    private Rectangle resetRect;
    private int mouseX = -1;
    private int mouseY = -1;

    public void updateMouse(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    public boolean handleClick(int x,
                               int y,
                               Runnable onResume,
                               Runnable onReset,
                               Runnable onToggleConstantSpeed,
                               IntConsumer onSpeedSelected) {
        for (int i = 0; i < speedReacts.length; i++) {
            if (speedReacts[i] != null && speedReacts[i].contains(x, y)) {
                onSpeedSelected.accept(i);
                return true;
            }
        }
        if (checkboxRect != null && checkboxRect.contains(x, y)) {
            onToggleConstantSpeed.run();
            return true;
        }
        if (resumeRect != null && resumeRect.contains(x, y)) {
            onResume.run();
            return true;
        }
        if (resetRect != null && resetRect.contains(x, y)) {
            onReset.run();
            return true;
        }
        return false;
    }

    public void paintCountdown(Graphics g, PongSnapshot snapshot) {
        long rem = PongEngine.COUNTDOWN_MS - (System.currentTimeMillis() - snapshot.countdownStartMs());
        int dig = (int) Math.ceil(rem / 1000.0);
        String text = dig >= 1 ? String.valueOf(dig) : "GO!";

        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, PongEngine.FIELD_WIDTH, PongEngine.FIELD_HEIGHT);

        g.setFont(new Font("Monospaced", Font.BOLD, 80));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(
                text,
                PongEngine.FIELD_WIDTH / 2 - fm.stringWidth(text) / 2,
                PongEngine.FIELD_HEIGHT / 2 + fm.getAscent() / 2 - 8);
    }

    public void paintPaused(Graphics g, PongSnapshot snapshot, String statusMessage) {
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, PongEngine.FIELD_WIDTH, PongEngine.FIELD_HEIGHT);

        g.setFont(new Font("Monospaced", Font.BOLD, 52));
        g.setColor(new Color(255, 220, 50));
        FontMetrics fm = g.getFontMetrics();
        String title = "PAUSED";
        g.drawString(
                title,
                PongEngine.FIELD_WIDTH / 2 - fm.stringWidth(title) / 2,
                PongEngine.FIELD_HEIGHT / 2 - 46);

        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        fm = g.getFontMetrics();
        String speedLabel = "BALL SPEED:";
        int labelW = fm.stringWidth(speedLabel);
        int cellW = 58;
        int cellH = 28;
        int gap = 6;
        int totalSpeedW = labelW + gap + speedReacts.length * cellW + (speedReacts.length - 1) * gap;
        int sx = PongEngine.FIELD_WIDTH / 2 - totalSpeedW / 2;
        int sy = PongEngine.FIELD_HEIGHT / 2 - 4;

        g.setColor(new Color(200, 200, 200));
        g.drawString(speedLabel, sx, sy + fm.getAscent());
        int bx = sx + labelW + gap;
        for (int i = 0; i < speedReacts.length; i++) {
            boolean active = i == snapshot.ballSpeedLevel();
            int rx = bx + i * (cellW + gap);
            speedReacts[i] = new Rectangle(rx, sy, cellW, cellH);
            boolean hovered = speedReacts[i].contains(mouseX, mouseY);

            if (active) {
                g.setColor(new Color(255, 200, 0));
                g2.fillRoundRect(rx, sy, cellW, cellH, 6, 6);
                g.setColor(Color.BLACK);
            } else if (hovered) {
                g.setColor(new Color(120, 100, 0));
                g2.fillRoundRect(rx, sy, cellW, cellH, 6, 6);
                g.setColor(new Color(255, 220, 100));
            } else {
                g.setColor(new Color(80, 80, 80));
                g2.fillRoundRect(rx, sy, cellW, cellH, 6, 6);
                g.setColor(new Color(180, 180, 180));
            }

            String label = SPEED_LABELS[i];
            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            fm = g.getFontMetrics();
            g.drawString(
                    label,
                    rx + cellW / 2 - fm.stringWidth(label) / 2,
                    sy + cellH / 2 + fm.getAscent() / 2 - 2);
        }

        int cby = PongEngine.FIELD_HEIGHT / 2 + 36;
        int cbSz = 16;
        int cbX = PongEngine.FIELD_WIDTH / 2 - 84;

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        fm = g.getFontMetrics();
        String cbLabel = "Constant Speed  (C)";
        int cbRowW = cbSz + 8 + fm.stringWidth(cbLabel);
        checkboxRect = new Rectangle(cbX, cby, cbRowW, cbSz + 4);
        boolean cbHovered = checkboxRect.contains(mouseX, mouseY);

        g2.setStroke(new BasicStroke(2f));
        g.setColor(snapshot.constantSpeed()
                ? new Color(255, 200, 0)
                : (cbHovered ? new Color(160, 140, 60) : new Color(100, 100, 100)));
        g2.drawRoundRect(cbX, cby, cbSz, cbSz, 4, 4);
        if (snapshot.constantSpeed()) {
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 200, 0));
            g2.drawLine(cbX + 3, cby + 8, cbX + 6, cby + 12);
            g2.drawLine(cbX + 6, cby + 12, cbX + 13, cby + 4);
        }
        g2.setStroke(new BasicStroke(1f));
        g.setColor(cbHovered ? new Color(255, 240, 160) : new Color(210, 210, 210));
        g.drawString(cbLabel, cbX + cbSz + 8, cby + fm.getAscent() - 1);

        int btnY = PongEngine.FIELD_HEIGHT / 2 + 64;
        int btnH = 26;
        int btnW = 110;
        int btnGap = 16;
        int resumeX = PongEngine.FIELD_WIDTH / 2 - btnW - btnGap / 2;
        int resetX = PongEngine.FIELD_WIDTH / 2 + btnGap / 2;

        resumeRect = new Rectangle(resumeX, btnY, btnW, btnH);
        resetRect = new Rectangle(resetX, btnY, btnW, btnH);

        paintButton(g2, resumeRect, "Resume  (ESC)", resumeRect.contains(mouseX, mouseY),
                new Color(40, 130, 60), new Color(60, 180, 80));
        paintButton(g2, resetRect, "Reset  (R)", resetRect.contains(mouseX, mouseY),
                new Color(130, 50, 40), new Color(190, 70, 55));

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(90, 90, 90));
        fm = g.getFontMetrics();
        String hint = "1-3 speed  |  C constant speed  |  ESC resume  |  R reset";
        g.drawString(
                hint,
                PongEngine.FIELD_WIDTH / 2 - fm.stringWidth(hint) / 2,
                PongEngine.FIELD_HEIGHT / 2 + 106);

        if (statusMessage != null && !statusMessage.isBlank()) {
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(255, 220, 160));
            fm = g.getFontMetrics();
            g.drawString(
                    statusMessage,
                    PongEngine.FIELD_WIDTH / 2 - fm.stringWidth(statusMessage) / 2,
                    PongEngine.FIELD_HEIGHT / 2 + 126);
        }
    }

    private void paintButton(Graphics2D g2,
                             Rectangle rect,
                             String label,
                             boolean hovered,
                             Color baseColor,
                             Color hoverColor) {
        g2.setColor(hovered ? hoverColor : baseColor);
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 8, 8);

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(hovered ? new Color(220, 255, 220) : new Color(160, 200, 160));
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 8, 8);
        g2.setStroke(new BasicStroke(1f));

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Color.WHITE);
        g2.drawString(
                label,
                rect.x + rect.width / 2 - fm.stringWidth(label) / 2,
                rect.y + rect.height / 2 + fm.getAscent() / 2 - 2);
    }
}
