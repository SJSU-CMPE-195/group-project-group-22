package edu.sjsu.spring2026.group32.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;

/**
 * Launcher section that displays the program launch cards.
 */
public class LaunchProgramsPanel extends JPanel {
    private static final int PANEL_HEIGHT = LaunchCardPanel.CARD_HEIGHT + 32;

    private final JButton launchBidirectionalButton;
    private final JButton launchHitTheZoneButton;
    private final JButton launchPongButton;

    public LaunchProgramsPanel() {
        super(new GridLayout(1, 3, 12, 0));
        setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder("Launch Program"),
                new EmptyBorder(10, 10, 10, 10)));
        setPreferredSize(new Dimension(0, PANEL_HEIGHT));
        setMinimumSize(new Dimension(0, PANEL_HEIGHT));

        launchBidirectionalButton = LaunchCardPanel.createLaunchButton(
                "Bidirectional Test",
                "Serial tester - uses the two Launcher connections",
                new Color(60, 120, 200));
        launchHitTheZoneButton = LaunchCardPanel.createLaunchButton(
                "Hit The Zone",
                "Game builds its own players from the passed HTZ connection",
                new Color(34, 160, 80));
        launchPongButton = LaunchCardPanel.createLaunchButton(
                "Pong",
                "Game builds its own hardware player from the passed Pong connection",
                new Color(160, 80, 200));

        add(new LaunchCardPanel(
                launchBidirectionalButton,
                "Hardware diagnostics and testing. Includes live voltage graphs and manual voltage injection."));
        add(new LaunchCardPanel(
                launchHitTheZoneButton,
                "A timing game where players score by hitting the ball in the zone. Supports hardware and software players."));
        add(new LaunchCardPanel(
                launchPongButton,
                "A two-player paddle game with hardware and software player support."));
    }

    public JButton getLaunchBidirectionalButton() {
        return launchBidirectionalButton;
    }

    public JButton getLaunchHitTheZoneButton() {
        return launchHitTheZoneButton;
    }

    public JButton getLaunchPongButton() {
        return launchPongButton;
    }
}
