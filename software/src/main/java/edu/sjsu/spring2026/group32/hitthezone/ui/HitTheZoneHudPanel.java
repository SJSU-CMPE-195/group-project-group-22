package edu.sjsu.spring2026.group32.hitthezone.ui;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneSnapshot;
import edu.sjsu.spring2026.group32.launcher.ui.ConnectionStatusPanel;
import edu.sjsu.spring2026.group32.player.model.BasePlayer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/**
 * Top HUD for Hit The Zone: hardware status, player rows, and info line.
 */
public class HitTheZoneHudPanel extends JPanel {
    private final JPanel playerRow;
    private final JLabel infoLabel;
    private JLabel[] playerLabels = new JLabel[0];

    public HitTheZoneHudPanel(SerialConnectionManager htzManager) {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

        ConnectionStatusPanel statusPanel = new ConnectionStatusPanel();
        statusPanel.addDevice("HTZ", htzManager);
        add(statusPanel, BorderLayout.NORTH);

        playerRow = new JPanel();
        add(playerRow, BorderLayout.CENTER);

        infoLabel = new JLabel("", SwingConstants.CENTER);
        infoLabel.setFont(infoLabel.getFont().deriveFont(12f));
        add(infoLabel, BorderLayout.SOUTH);
    }

    public void syncPlayers(List<? extends BasePlayer<?, ?>> players) {
        playerRow.removeAll();
        playerRow.setLayout(new GridLayout(players.size(), 1, 0, 2));
        playerLabels = new JLabel[players.size()];
        for (int i = 0; i < players.size(); i++) {
            JLabel label = new JLabel("", SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
            label.setForeground(HitTheZoneUiTheme.playerColor(i));
            playerLabels[i] = label;
            playerRow.add(label);
        }
        playerRow.revalidate();
        playerRow.repaint();
    }

    public void refresh(HitTheZoneSnapshot snapshot,
                        List<? extends BasePlayer<?, ?>> players,
                        String infoText) {
        int[] hits = snapshot.hits();
        int[] attempts = snapshot.attempts();
        for (int i = 0; i < players.size(); i++) {
            int h = hits[i];
            int a = attempts[i];
            String acc = a == 0 ? "-" : String.format("%d/%d (%.0f%%)", h, a, (h * 100.0) / a);
            String hitsPass = snapshot.totalPasses() == 0 ? "-" : String.format("%.2f", (double) h / snapshot.totalPasses());
            playerLabels[i].setText(String.format(
                    "%-12s [%s]   Hits: %d / %d   Accuracy: %s   Hits/Pass: %s",
                    players.get(i).getName(),
                    players.get(i).getType(),
                    h, a, acc, hitsPass));
        }
        infoLabel.setText(infoText);
    }
}
