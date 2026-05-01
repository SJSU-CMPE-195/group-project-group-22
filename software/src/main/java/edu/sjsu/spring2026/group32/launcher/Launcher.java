package edu.sjsu.spring2026.group32.launcher;

import edu.sjsu.spring2026.group32.bidirectionaltest.BidirectionalTest;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.pong.PongGame;
import edu.sjsu.spring2026.group32.sandbox.PoC_HitTheZone;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
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

    /** Non-null while a Bidirectional Test window is open (only one allowed at a time). */
    private BidirectionalTest bidirectionalInstance = null;
    /** Non-null while a Hit The Zone window is open (only one allowed at a time). */
    private PoC_HitTheZone htzInstance = null;
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

        htzPanel = new SerialConnectionPanel();
        htzManager = htzPanel.getConnectionManager();
        htzPanel.setExpectedChannelCount(1);   // 3-neuron single-channel firmware
        htzPanel.setBorder(new TitledBorder("Hit The Zone  -  3-neuron (single channel)"));
        htzPanel.setLogSink(msg -> log("[HTZ] " + msg));
        htzPanel.setConnectionListener(new SerialConnectionPanel.ConnectionListener() {
            @Override
            public void onConnected(SerialConnectionManager manager, String port) {
                log("Hit The Zone hardware ready on " + port);
                pongPanel.refreshPorts();   // hide the now-claimed HTZ port from Pong dropdown
            }

            @Override
            public void onDisconnected() {
                log("Hit The Zone hardware disconnected.");
                pongPanel.refreshPorts();   // restore the freed port in Pong dropdown
            }
        });

        pongPanel = new SerialConnectionPanel();
        pongManager = pongPanel.getConnectionManager();
        pongPanel.setExpectedChannelCount(2);  // 6-neuron dual-channel firmware
        pongPanel.setBorder(new TitledBorder("Pong  -  6-neuron (dual channel)"));
        pongPanel.setLogSink(msg -> log("[Pong] " + msg));
        pongPanel.setConnectionListener(new SerialConnectionPanel.ConnectionListener() {
            @Override
            public void onConnected(SerialConnectionManager manager, String port) {
                log("Pong hardware ready on " + port);
                htzPanel.refreshPorts();    // hide the now-claimed Pong port from HTZ dropdown
            }

            @Override
            public void onDisconnected() {
                log("Pong hardware disconnected.");
                htzPanel.refreshPorts();    // restore the freed port in HTZ dropdown
            }
        });

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

        logArea = new JTextArea(6, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(24, 24, 24));
        logArea.setForeground(new Color(200, 230, 200));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("Log"));

        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(buildConnectionArea(), BorderLayout.NORTH);
        root.add(buildLaunchPanel(), BorderLayout.CENTER);
        root.add(logScroll, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(760, 460));
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

    private JPanel buildLaunchPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 12, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder("Launch Program"),
                new EmptyBorder(8, 8, 8, 8)));

        panel.add(wrapLaunchButton(
                launchBidirectional,
                "HTZ + Pong connections\npassed from Launcher"));
        panel.add(wrapLaunchButton(
                launchHitTheZone,
                "Hit The Zone assembles\nits own software + hardware players"));
        panel.add(wrapLaunchButton(
                launchPong,
                "Pong assembles\nits own hardware controller"));
        return panel;
    }

    private JButton makeLaunchButton(String text, String tooltip, Color background) {
        JButton button = new JButton("<html><b>" + text + "</b></html>");
        button.setToolTipText(tooltip);
        button.setBackground(background);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(180, 60));
        button.setFocusPainted(false);
        return button;
    }

    private JPanel wrapLaunchButton(JButton button, String description) {
        JPanel cell = new JPanel(new BorderLayout(4, 4));
        cell.setOpaque(false);
        JLabel desc = new JLabel(
                "<html><center><small>" + description.replace("\n", "<br>") + "</small></center></html>",
                SwingConstants.CENTER);
        desc.setForeground(Color.DARK_GRAY);
        cell.add(button, BorderLayout.CENTER);
        cell.add(desc, BorderLayout.SOUTH);
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
        PoC_HitTheZone frame = PoC_HitTheZone.launchFromLauncher(htzManager);
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
