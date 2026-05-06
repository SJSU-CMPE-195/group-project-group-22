package edu.sjsu.spring2026.group32.launcher.ui;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Log area shown at the bottom of the launcher window.
 */
public class LauncherLogPanel extends JPanel {
    private static final int LOG_TEXT_ROWS = 6;

    private final JTextArea logArea;

    public LauncherLogPanel() {
        super(new BorderLayout());

        logArea = new JTextArea(LOG_TEXT_ROWS, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(24, 24, 24));
        logArea.setForeground(new Color(200, 230, 200));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("Log"));
        int minLogHeight = logArea.getHeight() * LOG_TEXT_ROWS + 24;
        logScroll.setPreferredSize(new Dimension(0, minLogHeight));
        logScroll.setMinimumSize(new Dimension(0, minLogHeight));

        add(logScroll, BorderLayout.CENTER);
    }

    public void appendLine(String text) {
        logArea.append(text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
