package edu.sjsu.spring2026.group32.hitthezone.ui;

import edu.sjsu.spring2026.group32.hitthezone.core.HitTheZoneEngine;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneSnapshot;
import edu.sjsu.spring2026.group32.player.model.BasePlayer;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Central visualization for Hit The Zone.
 */
public class HitTheZoneTrackPanel extends JPanel {
    private HitTheZoneSnapshot snapshot;
    private List<? extends BasePlayer<?, ?>> players = List.of();

    public HitTheZoneTrackPanel() {
        setPreferredSize(new Dimension(HitTheZoneEngine.WIDTH, HitTheZoneEngine.HEIGHT));
        setFocusable(true);
    }

    public void setViewModel(HitTheZoneSnapshot snapshot, List<? extends BasePlayer<?, ?>> players) {
        this.snapshot = snapshot;
        this.players = players;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (snapshot == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        final int trackH = 44;
        final int trackTop = HitTheZoneEngine.TRACK_Y - trackH / 2;

        g2.setColor(new Color(210, 210, 210));
        g2.fillRect(0, trackTop, getWidth(), trackH);

        int zoneStart = (HitTheZoneEngine.WIDTH - snapshot.zoneWidth()) / 2;
        g2.setColor(new Color(255, 228, 80));
        g2.fillRect(zoneStart, trackTop, snapshot.zoneWidth(), trackH);
        g2.setColor(new Color(160, 120, 0));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(zoneStart, trackTop, snapshot.zoneWidth(), trackH);

        final int barH = 10;
        final int barGap = 4;
        int barTopY = trackTop + trackH + 10;
        int[] hits = snapshot.hits();
        double maxRate = 0.0;
        if (snapshot.totalPasses() > 0) {
            for (int i = 0; i < players.size(); i++) {
                maxRate = Math.max(maxRate, hits[i] / (double) snapshot.totalPasses());
            }
        }

        for (int i = 0; i < players.size(); i++) {
            double rate = (snapshot.totalPasses() == 0 || maxRate == 0)
                    ? 0.0
                    : (hits[i] / (double) snapshot.totalPasses()) / maxRate;
            int filled = (int) (rate * snapshot.zoneWidth());
            Color color = HitTheZoneUiTheme.playerColor(i);

            g2.setColor(color.darker());
            g2.fillRect(zoneStart, barTopY, snapshot.zoneWidth(), barH);
            g2.setColor(color);
            g2.fillRect(zoneStart, barTopY, filled, barH);
            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(zoneStart, barTopY, snapshot.zoneWidth(), barH);
            g2.setColor(color);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
            String barLabel = snapshot.totalPasses() == 0
                    ? players.get(i).getName()
                    : String.format("%s (%.2f hits/pass)", players.get(i).getName(),
                    hits[i] / (double) snapshot.totalPasses());
            g2.drawString(barLabel, zoneStart + snapshot.zoneWidth() + 6, barTopY + barH - 1);
            barTopY += barH + barGap;
        }

        g2.setColor(Color.RED);
        g2.fillOval(snapshot.ballX(), HitTheZoneEngine.TRACK_Y - HitTheZoneEngine.BALL_DIAM / 2,
                HitTheZoneEngine.BALL_DIAM, HitTheZoneEngine.BALL_DIAM);
        g2.setColor(new Color(120, 0, 0));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(snapshot.ballX(), HitTheZoneEngine.TRACK_Y - HitTheZoneEngine.BALL_DIAM / 2,
                HitTheZoneEngine.BALL_DIAM, HitTheZoneEngine.BALL_DIAM);

        g2.setColor(new Color(100, 80, 0));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
        FontMetrics fm = g2.getFontMetrics();
        String label = "ZONE";
        g2.drawString(label, zoneStart + (snapshot.zoneWidth() - fm.stringWidth(label)) / 2, trackTop - 4);
    }
}
