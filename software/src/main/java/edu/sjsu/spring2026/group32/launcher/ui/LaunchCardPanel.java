package edu.sjsu.spring2026.group32.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Single launch action card with a button and short description.
 */
public class LaunchCardPanel extends JPanel {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 60;
    private static final int DESCRIPTION_WIDTH = 220;
    private static final int DESCRIPTION_HEIGHT = 68;
    private static final int CARD_GAP = 8;
    private static final int CARD_PADDING = 8;
    public static final int CARD_HEIGHT =
            BUTTON_HEIGHT + DESCRIPTION_HEIGHT + CARD_GAP + (CARD_PADDING * 2);

    public LaunchCardPanel(JButton button, String description) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setPreferredSize(new Dimension(BUTTON_WIDTH, CARD_HEIGHT));
        setMinimumSize(new Dimension(BUTTON_WIDTH, CARD_HEIGHT));
        setBorder(new EmptyBorder(CARD_PADDING, CARD_PADDING, CARD_PADDING, CARD_PADDING));

        JTextPane desc = new JTextPane();
        desc.setText(description.replace(" \n", "\n"));
        desc.setEditable(false);
        desc.setFocusable(false);
        desc.setOpaque(false);
        desc.setForeground(Color.DARK_GRAY);
        desc.setFont(new JLabel().getFont().deriveFont(Font.PLAIN, 11f));
        StyledDocument doc = desc.getStyledDocument();
        SimpleAttributeSet centered = new SimpleAttributeSet();
        StyleConstants.setAlignment(centered, StyleConstants.ALIGN_CENTER);
        doc.setParagraphAttributes(0, doc.getLength(), centered, false);
        desc.setPreferredSize(new Dimension(DESCRIPTION_WIDTH, DESCRIPTION_HEIGHT));
        desc.setMinimumSize(new Dimension(DESCRIPTION_WIDTH, DESCRIPTION_HEIGHT));
        desc.setMaximumSize(new Dimension(DESCRIPTION_WIDTH, DESCRIPTION_HEIGHT));

        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setMinimumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, BUTTON_HEIGHT));

        add(button);
        add(Box.createRigidArea(new Dimension(0, CARD_GAP)));
        add(desc);
    }

    public static JButton createLaunchButton(String text, String tooltip, Color background) {
        JButton button = new JButton("<html><b>" + text + "</b></html>");
        button.setToolTipText(tooltip);
        button.setBackground(background);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        return button;
    }
}
