package edu.sjsu.spring2026.group32.hitthezone.ui;

import edu.sjsu.spring2026.group32.player.model.BasePlayer;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Bottom control strip for Hit The Zone.
 */
public class HitTheZoneControlPanel extends JPanel {
    final JButton pauseButton;
    final JButton resetButton;
    final List<JButton> humanScoreButtons = new ArrayList<>();
    private final Runnable onPause;
    private final Runnable onReset;
    private final IntConsumer onScore;

    public HitTheZoneControlPanel(Runnable onPause, Runnable onReset, IntConsumer onScore) {
        super(new FlowLayout(FlowLayout.CENTER, 10, 6));
        this.onPause = onPause;
        this.onReset = onReset;
        this.onScore = onScore;

        pauseButton = new JButton("Pause (Esc)");
        pauseButton.setFocusable(false);
        pauseButton.addActionListener(e -> this.onPause.run());
        add(pauseButton);

        resetButton = new JButton("Reset (R)");
        resetButton.setFocusable(false);
        resetButton.addActionListener(e -> this.onReset.run());
        add(resetButton);
    }

    public void syncPlayers(List<? extends BasePlayer<?, ?>> players) {
        for (JButton button : humanScoreButtons) {
            remove(button);
        }
        humanScoreButtons.clear();

        for (int i = 0; i < players.size(); i++) {
            if (players.get(i) instanceof KeyListener) {
                final int index = i;
                JButton scoreButton = new JButton("Score (Space) - " + players.get(i).getName());
                scoreButton.setFocusable(false);
                scoreButton.setForeground(HitTheZoneUiTheme.playerColor(i));
                scoreButton.setFont(scoreButton.getFont().deriveFont(Font.BOLD));
                scoreButton.addActionListener(e -> onScore.accept(index));
                humanScoreButtons.add(scoreButton);
                add(scoreButton);
            }
        }

        revalidate();
        repaint();
    }

    public void setPaused(boolean paused) {
        pauseButton.setText(paused ? "Resume (Esc)" : "Pause (Esc)");
        for (JButton button : humanScoreButtons) {
            button.setEnabled(!paused);
        }
    }
}
