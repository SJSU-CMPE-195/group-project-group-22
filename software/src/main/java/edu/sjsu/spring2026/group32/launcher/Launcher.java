package edu.sjsu.spring2026.group32.launcher;

import edu.sjsu.spring2026.group32.BidirectionalTest;
import edu.sjsu.spring2026.group32.hardware.HardwareSignalSource;
import edu.sjsu.spring2026.group32.hardware.NeuralSignalParser;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.player.BasePlayer;
import edu.sjsu.spring2026.group32.player.HumanPlayer;
import edu.sjsu.spring2026.group32.pong.PongAction;
import edu.sjsu.spring2026.group32.pong.PongGame;
import edu.sjsu.spring2026.group32.pong.PongSoftwareAI;
import edu.sjsu.spring2026.group32.pong.PongState;
import edu.sjsu.spring2026.group32.sandbox.HitTheZoneAction;
import edu.sjsu.spring2026.group32.sandbox.HitTheZoneHardwareAI;
import edu.sjsu.spring2026.group32.sandbox.HitTheZoneSoftwareAI;
import edu.sjsu.spring2026.group32.sandbox.HitTheZoneState;
import edu.sjsu.spring2026.group32.sandbox.PoC_HitTheZone;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Central launch hub for all ESP32-backed programs.
 *
 * <p>The Launcher owns a single {@link SerialConnectionManager} instance and
 * passes it to each program via constructor injection.  This ensures all
 * programs share the same already-opened COM port without each needing their
 * own connection UI.</p>
 *
 * <p>Workflow:
 * <ol>
 *   <li>Select COM port and press <b>Connect</b>.</li>
 *   <li>Choose a program to launch — it receives the live connection.</li>
 *   <li>Close the program window to return to the Launcher (connection stays open).</li>
 *   <li>Press <b>Disconnect</b> when finished to release the COM port.</li>
 * </ol>
 * </p>
 */
public class Launcher extends JFrame {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── UI ────────────────────────────────────────────────────────────────────
    private final SerialConnectionPanel connectionPanel;
    private final JTextArea             logArea;
    private final JButton               launchBidirectional;
    private final JButton               launchHitTheZone;
    private final JButton               launchPong;

    // ── State ─────────────────────────────────────────────────────────────────
    /** The shared, already-connected manager. Non-null only while connected. */
    private SerialConnectionManager connectionManager;

    // =========================================================================
    //  Entry point
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Launcher().setVisible(true));
    }

    // =========================================================================
    //  Constructor
    // =========================================================================
    public Launcher() {
        super("ESP32 Program Launcher");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (connectionManager != null) connectionPanel.disconnect();
                dispose();
                System.exit(0);
            }
        });

        // ── Connection panel ──────────────────────────────────────────────────
        connectionPanel = new SerialConnectionPanel();
        connectionPanel.setLogSink(this::log);
        connectionPanel.setConnectionListener(new SerialConnectionPanel.ConnectionListener() {
            @Override
            public void onConnected(SerialConnectionManager mgr, String portLabel) {
                connectionManager = mgr;
                setLaunchButtonsEnabled(true);
                log("Ready — select a program to launch.");
            }

            @Override
            public void onDisconnected() {
                connectionManager = null;
                setLaunchButtonsEnabled(false);
                log("Disconnected. Select a port and reconnect to launch programs.");
            }
        });

        // ── Launch buttons ────────────────────────────────────────────────────
        launchBidirectional = makeLaunchButton("Bidirectional Test",
                "Interactive ESP32 ↔ Java serial tester with voltage graph and injection panel",
                new Color(60, 120, 200));

        launchHitTheZone    = makeLaunchButton("Hit The Zone",
                "Proof-of-concept game: hardware AI vs software AIs vs human player",
                new Color(34, 160, 80));

        launchPong          = makeLaunchButton("Pong",
                "Classic Pong with software AI players",
                new Color(160, 80, 200));

        setLaunchButtonsEnabled(false);

        launchBidirectional.addActionListener(e -> openBidirectionalTest());
        launchHitTheZone   .addActionListener(e -> openHitTheZone());
        launchPong         .addActionListener(e -> openPong());

        // ── Log area ──────────────────────────────────────────────────────────
        logArea = new JTextArea(6, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(24, 24, 24));
        logArea.setForeground(new Color(200, 230, 200));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("Log"));

        // ── Layout ────────────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        root.add(connectionPanel, BorderLayout.NORTH);
        root.add(buildLaunchPanel(), BorderLayout.CENTER);
        root.add(logScroll, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(700, 400));
        setLocationRelativeTo(null);

        log("Welcome! Connect to an ESP32 COM port to enable program launch.");
    }

    // =========================================================================
    //  Program launchers
    // =========================================================================

    private void openBidirectionalTest() {
        if (assertConnected()) return;
        log("Launching Bidirectional Test…");

        BidirectionalTest frame = new BidirectionalTest(connectionManager);
        trackLaunchedWindow(frame, "Bidirectional Test");
        frame.setVisible(true);
    }

    private void openHitTheZone() {
        if (assertConnected()) return;
        log("Launching Hit The Zone…");

        // Build the hardware stack using the shared connection.
        List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players = getBasePlayers();

        PoC_HitTheZone frame = new PoC_HitTheZone(players);
        // Override EXIT_ON_CLOSE so closing the game doesn't kill the Launcher.
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        trackLaunchedWindow(frame, "Hit The Zone");
        frame.setVisible(true);
    }

    private List<BasePlayer<HitTheZoneState, HitTheZoneAction>> getBasePlayers() {
        NeuralSignalParser   parser   = new NeuralSignalParser();
        HardwareSignalSource src      = new HardwareSignalSource(connectionManager, parser);
        HitTheZoneHardwareAI hwPlayer = new HitTheZoneHardwareAI("Neural", src);

        Map<Integer, HitTheZoneAction> bindings =
                Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE);

        return List.of(
                new HitTheZoneSoftwareAI("Bot Alpha", 3),
                new HitTheZoneSoftwareAI("Bot Beta",  9),
                hwPlayer,
                new HumanPlayer<>("Human", bindings, null)
        );
    }

    private void openPong() {
        if (assertConnected()) return;
        log("Launching Pong…");

        BasePlayer<PongState, PongAction> ai1 = new PongSoftwareAI("SoftwareAI 1");
        BasePlayer<PongState, PongAction> ai2 = new PongSoftwareAI("SoftwareAI 2");

        PongGame gamePanel = new PongGame(ai1, ai2);

        JFrame frame = new JFrame(String.format("Pong — %s vs %s",
                ai1.getName(), ai2.getName()));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setResizable(false);
        frame.add(gamePanel);
        frame.pack();
        frame.setLocationRelativeTo(null);

        trackLaunchedWindow(frame, "Pong");
        frame.setVisible(true);
        gamePanel.start();
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    /**
     * Attaches a WindowListener so the Launcher logs when the child window
     * is closed.  The connection is left open.
     */
    private void trackLaunchedWindow(Window window, String name) {
        window.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                log(name + " closed. Connection remains open.");
            }
        });
    }

    private boolean assertConnected() {
        if (connectionManager == null || !connectionManager.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Not connected. Please connect to an ESP32 first.",
                    "Not Connected", JOptionPane.WARNING_MESSAGE);
            return true;
        }
        return false;
    }

    private void setLaunchButtonsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            launchBidirectional.setEnabled(enabled);
            launchHitTheZone   .setEnabled(enabled);
            launchPong         .setEnabled(enabled);
        });
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String ts = LocalTime.now().format(TIME_FMT);
            logArea.append("[" + ts + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ── Build the row of launch buttons ──────────────────────────────────────
    private JPanel buildLaunchPanel() {
        JPanel p = new JPanel(new GridLayout(1, 3, 12, 0));
        p.setBorder(new TitledBorder("Launch Program"));
        p.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder("Launch Program  (connect first)"),
                new EmptyBorder(8, 8, 8, 8)));

        p.add(wrapLaunchButton(launchBidirectional,
                "Interactive serial tester with\nvoltage graph & injection panel"));
        p.add(wrapLaunchButton(launchHitTheZone,
                "Hardware AI vs software AIs\nvs human player"));
        p.add(wrapLaunchButton(launchPong,
                "Classic Pong with\nsoftware AI players"));

        return p;
    }

    private JButton makeLaunchButton(String text, String tooltip, Color bg) {
        JButton btn = new JButton("<html><b>" + text + "</b></html>");
        btn.setToolTipText(tooltip);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(180, 60));
        btn.setFocusPainted(false);
        return btn;
    }

    private JPanel wrapLaunchButton(JButton btn, String description) {
        JPanel cell = new JPanel(new BorderLayout(4, 4));
        cell.setOpaque(false);
        JLabel desc = new JLabel("<html><center><small>" +
                description.replace("\n", "<br>") +
                "</small></center></html>", SwingConstants.CENTER);
        desc.setForeground(Color.DARK_GRAY);
        cell.add(btn, BorderLayout.CENTER);
        cell.add(desc, BorderLayout.SOUTH);
        return cell;
    }
}
