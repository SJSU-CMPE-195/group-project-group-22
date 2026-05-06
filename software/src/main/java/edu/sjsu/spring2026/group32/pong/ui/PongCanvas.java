package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.annotations.GeneratedExcludeFromCoverage;
import edu.sjsu.spring2026.group32.pong.core.PongEngine;
import edu.sjsu.spring2026.group32.pong.model.PongGameState;
import edu.sjsu.spring2026.group32.pong.model.PongSnapshot;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.function.IntConsumer;

/**
 * Visual field and overlay surface for Pong.
 */
public class PongCanvas extends JPanel {
    private final PongPauseOverlay pauseOverlay = new PongPauseOverlay();

    private PongSnapshot snapshot;
    private String topPlayerName = "";
    private String bottomPlayerName = "";
    private String statusMessage;
    private String bottomHintMessage;

    public PongCanvas(Runnable onResume,
                      Runnable onReset,
                      Runnable onToggleConstantSpeed,
                      IntConsumer onSpeedSelected) {

        setPreferredSize(new Dimension(PongEngine.FIELD_WIDTH, PongEngine.FIELD_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(false);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (snapshot == null || snapshot.gameState() != PongGameState.PAUSED) {
                    return;
                }
                if (pauseOverlay.handleClick(
                        e.getX(),
                        e.getY(),
                        onResume,
                        onReset,
                        onToggleConstantSpeed,
                        onSpeedSelected)) {
                    repaint();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (snapshot == null || snapshot.gameState() != PongGameState.PAUSED) {
                    return;
                }
                pauseOverlay.updateMouse(e.getX(), e.getY());
                repaint();
            }
        });
    }

    public void setSnapshot(PongSnapshot snapshot, String topPlayerName, String bottomPlayerName) {
        this.snapshot = snapshot;
        this.topPlayerName = topPlayerName;
        this.bottomPlayerName = bottomPlayerName;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public void setBottomHintMessage(String bottomHintMessage) {
        this.bottomHintMessage = bottomHintMessage;
    }

    @Override
    @GeneratedExcludeFromCoverage
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (snapshot == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(55, 55, 55));
        for (int x = 0; x < PongEngine.FIELD_WIDTH; x += 30) {
            g.fillRect(x, PongEngine.FIELD_HEIGHT / 2 - 2, 18, 4);
        }

        g.setColor(Color.WHITE);
        g2.fillRoundRect(snapshot.topPaddleX(), PongEngine.TOP_PADDLE_Y,
                PongEngine.PADDLE_WIDTH, PongEngine.PADDLE_HEIGHT, 8, 8);
        g2.fillRoundRect(snapshot.bottomPaddleX(), PongEngine.BOTTOM_PADDLE_Y,
                PongEngine.PADDLE_WIDTH, PongEngine.PADDLE_HEIGHT, 8, 8);

        g2.fillOval(snapshot.ballX(), snapshot.ballY(), PongEngine.BALL_SIZE, PongEngine.BALL_SIZE);

        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        FontMetrics fm = g.getFontMetrics();
        String topScore = String.valueOf(snapshot.topScore());
        String bottomScore = String.valueOf(snapshot.bottomScore());
        int cx = PongEngine.FIELD_WIDTH / 2;
        g.setColor(new Color(200, 200, 200));
        g.drawString(topScore, cx - fm.stringWidth(topScore) / 2, PongEngine.FIELD_HEIGHT / 2 - 20);
        g.drawString(bottomScore, cx - fm.stringWidth(bottomScore) / 2,
                PongEngine.FIELD_HEIGHT / 2 + fm.getAscent() + 4);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(120, 120, 120));
        g.drawString(topPlayerName, 8, PongEngine.TOP_PADDLE_Y + PongEngine.PADDLE_HEIGHT + 14);
        g.drawString(bottomPlayerName, 8, PongEngine.BOTTOM_PADDLE_Y - 4);

        if (bottomHintMessage != null && !bottomHintMessage.isBlank()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(180, 180, 180));
            FontMetrics hintMetrics = g.getFontMetrics();
            g.drawString(
                    bottomHintMessage,
                    PongEngine.FIELD_WIDTH / 2 - hintMetrics.stringWidth(bottomHintMessage) / 2,
                    PongEngine.FIELD_HEIGHT - 8);
        }

        switch (snapshot.gameState()) {
            case COUNTDOWN -> pauseOverlay.paintCountdown(g, snapshot);
            case PAUSED -> pauseOverlay.paintPaused(g, snapshot, statusMessage);
            case PLAYING -> {
            }
        }
    }
}
