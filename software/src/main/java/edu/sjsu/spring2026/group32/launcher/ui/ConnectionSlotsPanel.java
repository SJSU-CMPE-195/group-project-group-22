package edu.sjsu.spring2026.group32.launcher.ui;

import javax.swing.JPanel;
import java.awt.GridLayout;

/**
 * Vertical stack of serial connection slots used by the launcher.
 */
public class ConnectionSlotsPanel extends JPanel {
    public ConnectionSlotsPanel(SerialConnectionPanel topPanel, SerialConnectionPanel bottomPanel) {
        super(new GridLayout(2, 1, 8, 0));
        add(topPanel);
        add(bottomPanel);
    }
}
