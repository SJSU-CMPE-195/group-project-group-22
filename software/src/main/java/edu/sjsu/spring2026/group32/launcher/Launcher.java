package edu.sjsu.spring2026.group32.launcher;

import edu.sjsu.spring2026.group32.bidirectionaltest.BidirectionalTest;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.launcher.ui.SerialConnectionPanel;
import edu.sjsu.spring2026.group32.pong.PongGame;
import edu.sjsu.spring2026.group32.hitthezone.HitTheZoneGame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.Collections;
import java.util.Set;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Central launch hub for all ESP32-backed programs.
 *
 * <p>The Launcher owns the two serial connection panels and the live
 * connection managers they produce. Individual games no longer assemble their
 * own hardware stacks here. Instead, the Launcher passes the relevant manager
 * into each game module and lets that module decide how to build its hardware
 * players and status UI.</p>
 */
public class Launcher extends JFrame {
    private static final int WINDOW_MIN_WIDTH = 820;
    private static final int WINDOW_MIN_HEIGHT = 520;
    private static final int LAUNCH_BUTTON_WIDTH = 220;
    private static final int LAUNCH_BUTTON_HEIGHT = 60;
    private static final int LAUNCH_DESCRIPTION_WIDTH = 220;
    private static final int LAUNCH_DESCRIPTION_HEIGHT = 68;
    private static final int LAUNCH_CARD_GAP = 8;
    private static final int LAUNCH_CARD_PADDING = 8;
    private static final int LAUNCH_CARD_HEIGHT =
            LAUNCH_BUTTON_HEIGHT + LAUNCH_DESCRIPTION_HEIGHT + LAUNCH_CARD_GAP
                    + (LAUNCH_CARD_PADDING * 2);
    private static final int LAUNCH_PANEL_HEIGHT = LAUNCH_CARD_HEIGHT + 32;
    private static final int LOG_TEXT_ROWS = 6;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    SerialConnectionPanel htzPanel;
    SerialConnectionPanel pongPanel;

    /** Long-lived manager for the HTZ slot. */
    SerialConnectionManager htzManager;
    /** Long-lived manager for the Pong slot. */
    SerialConnectionManager pongManager;

    private final JButton launchBidirectional;
    private final JButton launchHitTheZone;
    private final JButton launchPong;
    private final JTextArea logArea;
    private final JScrollPane logScroll;

    /** Non-null while a Bidirectional Test window is open (only one allowed at a time). */
    private BidirectionalTest bidirectionalInstance = null;
    /** Non-null while a Hit The Zone window is open (only one allowed at a time). */
    private HitTheZoneGame htzInstance = null;
    /** Non-null while a Pong window is open (only one allowed at a time). */
    private JFrame pongInstance = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Launcher().setVisible(true));
    }

    public Launcher() {
        super("ESP32 Program Launcher");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (htzManager != null) {
                    htzPanel.disconnect();
                }
                if (pongManager != null) {
                    pongPanel.disconnect();
                }
                dispose();
                System.exit(0);
            }
        });

        htzPanel = buildConnectionPanel(
                "Hit The Zone  -  3-neuron (single channel)",
                1,
                "[HTZ] ",
                "Hit The Zone");
        htzManager = htzPanel.getConnectionManager();

        pongPanel = buildConnectionPanel(
                "Pong  -  6-neuron (dual channel)",
                2,
                "[Pong] ",
                "Pong");
        pongManager = pongPanel.getConnectionManager();

        // Each panel excludes ports already claimed by the other panel.
        // The supplier is evaluated lazily on every refreshPorts() call.
        htzPanel.setExcludedPortsSupplier(() -> {
            String p = pongPanel.getConnectedPortName();
            return p != null ? Set.of(p) : Collections.emptySet();
        });
        pongPanel.setExcludedPortsSupplier(() -> {
            String p = htzPanel.getConnectedPortName();
            return p != null ? Set.of(p) : Collections.emptySet();
        });

        launchBidirectional = makeLaunchButton(
                "Bidirectional Test",
                "Serial tester - uses the two Launcher connections",
                new Color(60, 120, 200));
        launchHitTheZone = makeLaunchButton(
                "Hit The Zone",
                "Game builds its own players from the passed HTZ connection",
                new Color(34, 160, 80));
        launchPong = makeLaunchButton(
                "Pong",
                "Game builds its own hardware player from the passed Pong connection",
                new Color(160, 80, 200));

        launchBidirectional.addActionListener(e -> openBidirectionalTest());
        launchHitTheZone.addActionListener(e -> openHitTheZone());
        launchPong.addActionListener(e -> openPong());

        logArea = new JTextArea(LOG_TEXT_ROWS, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(24, 24, 24));
        logArea.setForeground(new Color(200, 230, 200));

        logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("Log"));
        int minLogHeight = logArea.getHeight() * LOG_TEXT_ROWS + 24;
        logScroll.setPreferredSize(new Dimension(0, minLogHeight));
        logScroll.setMinimumSize(new Dimension(0, minLogHeight));

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        JPanel topSection = new JPanel(new BorderLayout(10, 10));
        topSection.setOpaque(false);
        topSection.add(buildConnectionArea(), BorderLayout.NORTH);
        topSection.add(buildLaunchPanel(), BorderLayout.CENTER);

        root.add(topSection, BorderLayout.NORTH);
        root.add(logScroll, BorderLayout.CENTER);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(WINDOW_MIN_WIDTH, WINDOW_MIN_HEIGHT));
        refreshLauncherLayout();
        setLocationRelativeTo(null);

        log("Connect your ESP32 devices to enable hardware players.");
        log("Programs can be launched without hardware - neural players will be skipped.");
    }

    private JPanel buildConnectionArea() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 8, 0));
        panel.add(htzPanel);
        panel.add(pongPanel);
        return panel;
    }

    private SerialConnectionPanel buildConnectionPanel(String title,
                                                       int expectedChannelCount,
                                                       String logPrefix,
                                                       String programName) {
        SerialConnectionPanel panel = new SerialConnectionPanel();
        panel.setExpectedChannelCount(expectedChannelCount);
        panel.setBorder(new TitledBorder(title));
        panel.setLogSink(msg -> log(logPrefix + msg));
        panel.setConnectionListener(new SerialConnectionPanel.ConnectionListener() {
            @Override
            public void onConnected(SerialConnectionManager manager, String port) {
                log(programName + " hardware ready on " + port);
                refreshSiblingPorts(panel);
                refreshLauncherLayout();
            }

            @Override
            public void onDisconnected() {
                log(programName + " hardware disconnected.");
                refreshSiblingPorts(panel);
                refreshLauncherLayout();
            }
        });
        return panel;
    }

    private void refreshSiblingPorts(SerialConnectionPanel sourcePanel) {
        if (sourcePanel != htzPanel && htzPanel != null) {
            htzPanel.refreshPorts();
        }
        if (sourcePanel != pongPanel && pongPanel != null) {
            pongPanel.refreshPorts();
        }
    }

    private JPanel buildLaunchPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 12, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder("Launch Program"),
                new EmptyBorder(10, 10, 10, 10)));
        panel.setPreferredSize(new Dimension(0, LAUNCH_PANEL_HEIGHT));
        panel.setMinimumSize(new Dimension(0, LAUNCH_PANEL_HEIGHT));

        panel.add(wrapLaunchButton(
                launchBidirectional,
                "Hardware diagnostics and testing. Includes live voltage graphs and manual voltage injection."));
        panel.add(wrapLaunchButton(
                launchHitTheZone,
                "A timing game where players score by hitting the ball in the zone. Supports hardware and software players."));
        panel.add(wrapLaunchButton(
                launchPong,
                "A two-player paddle game with hardware and software player support."));
        return panel;
    }

    private void refreshLauncherLayout() {
        revalidate();
        repaint();
        Dimension preferred = getContentPane().getPreferredSize();
        int minHeight = Math.max(WINDOW_MIN_HEIGHT, preferred.height + 24);
        setMinimumSize(new Dimension(WINDOW_MIN_WIDTH, minHeight));
    }

    private JButton makeLaunchButton(String text, String tooltip, Color background) {
        JButton button = new JButton("<html><b>" + text + "</b></html>");
        button.setToolTipText(tooltip);
        button.setBackground(background);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(LAUNCH_BUTTON_WIDTH, LAUNCH_BUTTON_HEIGHT));
        button.setMinimumSize(new Dimension(LAUNCH_BUTTON_WIDTH, LAUNCH_BUTTON_HEIGHT));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, LAUNCH_BUTTON_HEIGHT));
        button.setFocusPainted(false);
        return button;
    }

    private JPanel wrapLaunchButton(JButton button, String description) {
        JPanel cell = new JPanel();
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setOpaque(false);
        cell.setPreferredSize(new Dimension(LAUNCH_BUTTON_WIDTH, LAUNCH_CARD_HEIGHT));
        cell.setMinimumSize(new Dimension(LAUNCH_BUTTON_WIDTH, LAUNCH_CARD_HEIGHT));
        cell.setBorder(new EmptyBorder(
                LAUNCH_CARD_PADDING,
                LAUNCH_CARD_PADDING,
                LAUNCH_CARD_PADDING,
                LAUNCH_CARD_PADDING));
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
        desc.setPreferredSize(new Dimension(LAUNCH_DESCRIPTION_WIDTH, LAUNCH_DESCRIPTION_HEIGHT));
        desc.setMinimumSize(new Dimension(LAUNCH_DESCRIPTION_WIDTH, LAUNCH_DESCRIPTION_HEIGHT));
        desc.setMaximumSize(new Dimension(LAUNCH_DESCRIPTION_WIDTH, LAUNCH_DESCRIPTION_HEIGHT));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);
        cell.add(button);
        cell.add(Box.createRigidArea(new Dimension(0, LAUNCH_CARD_GAP)));
        cell.add(desc);
        return cell;
    }

    private void openBidirectionalTest() {
        if (bidirectionalInstance != null) {
            return;
        }
        log("Launching Bidirectional Test...");
        BidirectionalTest frame = new BidirectionalTest(htzManager, pongManager);
        bidirectionalInstance = frame;
        launchBidirectional.setEnabled(false);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                bidirectionalInstance = null;
                launchBidirectional.setEnabled(true);
                log("Bidirectional Test closed.");
            }
        });
        frame.setVisible(true);
    }

    private void openHitTheZone() {
        if (htzInstance != null) {
            return;
        }
        log("Launching Hit The Zone...");
        HitTheZoneGame frame = HitTheZoneGame.launchFromLauncher(htzManager);
        htzInstance = frame;
        launchHitTheZone.setEnabled(false);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                htzInstance = null;
                launchHitTheZone.setEnabled(true);
                log("Hit The Zone closed.");
            }
        });

        if (htzManager != null && htzManager.isConnected()) {
            log("  -> HTZ hardware passed to Hit The Zone.");
        } else {
            log("  -> HTZ device not connected. Launching without neural player.");
        }
    }

    private void openPong() {
        if (pongInstance != null) {
            return;
        }
        log("Launching Pong...");
        JFrame frame = PongGame.launchFromLauncher(pongManager);
        pongInstance = frame;
        launchPong.setEnabled(false);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                pongInstance = null;
                launchPong.setEnabled(true);
                log("Pong closed.");
            }
        });

        if (pongManager != null && pongManager.isConnected() && pongManager.getDeviceChannelCount() >= 2) {
            log("  -> Pong hardware passed to Pong.");
        } else if (pongManager != null && pongManager.isConnected()) {
            int channelCount = pongManager.getDeviceChannelCount();
            log("  -> Pong device has " + channelCount + " ch (need 2). Launching without neural player.");
        } else {
            log("  -> Pong device not connected. Launching without neural player.");
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalTime.now().format(TIME_FMT);
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
