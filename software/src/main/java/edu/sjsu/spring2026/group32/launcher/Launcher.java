package edu.sjsu.spring2026.group32.launcher;

import edu.sjsu.spring2026.group32.BidirectionalTest;
import edu.sjsu.spring2026.group32.hardware.HardwareSignalSource;
import edu.sjsu.spring2026.group32.hardware.NeuralSignalParser;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.player.BasePlayer;
import edu.sjsu.spring2026.group32.player.HumanPlayer;
import edu.sjsu.spring2026.group32.pong.PongGame;
import edu.sjsu.spring2026.group32.pong.PongHardwareAI;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Central launch hub for all ESP32-backed programs.
 * There are exactly two {@link SerialConnectionPanel} instances in the entire
 * application, both owned by this class.  No other window manages its own port.
 *
 * <ul>
 *   <li>Hit The Zone  — 3-neuron config, single ADC channel (GPIO34).</li>
 *   <li>Pong          — 6-neuron config, dual ADC channels:
 *                       GPIO34 = LEFT, GPIO35 = RIGHT.
 *                       Both channels combine into a single PongHardwareAI.</li>
 *   <li>Bidirectional Test — receives both Launcher-managed connections
 *                            (htzManager + pongManager) and lets the user
 *                            switch between them via a device selector.</li>
 * </ul>
 */
public class Launcher extends JFrame {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── Per-device connection panels ──────────────────────────────────────────
    SerialConnectionPanel htzPanel;
    SerialConnectionPanel pongPanel;

    /** Live manager for the HTZ device; null when disconnected. */
    SerialConnectionManager htzManager;
    /** Live manager for the Pong device; null when disconnected. */
    SerialConnectionManager pongManager;

    // ── Launch buttons ────────────────────────────────────────────────────────
    private final JButton launchBidirectional;
    private final JButton launchHitTheZone;
    private final JButton launchPong;

    // ── Log ───────────────────────────────────────────────────────────────────
    private final JTextArea logArea;

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
                if (htzManager  != null) htzPanel .disconnect();
                if (pongManager != null) pongPanel.disconnect();
                dispose();
                System.exit(0);
            }
        });

        // ── Hit The Zone connection panel (3-neuron, single channel) ──────────
        htzPanel = new SerialConnectionPanel();
        htzPanel.setBorder(new TitledBorder("Hit The Zone  —  3-neuron (single channel)"));
        htzPanel.setLogSink(msg -> log("[HTZ] " + msg));
        htzPanel.setConnectionListener(new SerialConnectionPanel.ConnectionListener() {
            @Override public void onConnected(SerialConnectionManager mgr, String port) {
                htzManager = mgr;
                log("Hit The Zone hardware ready on " + port);
            }
            @Override public void onDisconnected() {
                htzManager = null;
                log("Hit The Zone hardware disconnected.");
            }
        });

        // ── Pong connection panel (6-neuron, dual channel) ────────────────────
        pongPanel = new SerialConnectionPanel();
        pongPanel.setBorder(new TitledBorder("Pong  —  6-neuron (dual channel)"));
        pongPanel.setLogSink(msg -> log("[Pong] " + msg));
        pongPanel.setConnectionListener(new SerialConnectionPanel.ConnectionListener() {
            @Override public void onConnected(SerialConnectionManager mgr, String port) {
                pongManager = mgr;
                log("Pong hardware ready on " + port);
            }
            @Override public void onDisconnected() {
                pongManager = null;
                log("Pong hardware disconnected.");
            }
        });

        // ── Launch buttons (always enabled) ───────────────────────────────────
        launchBidirectional = makeLaunchButton("Bidirectional Test",
                "Serial tester — uses the two Launcher connections",
                new Color(60, 120, 200));

        launchHitTheZone    = makeLaunchButton("Hit The Zone",
                "3-neuron hardware AI + software AIs + human player",
                new Color(34, 160, 80));

        launchPong          = makeLaunchButton("Pong",
                "Hardware AI enabled when Pong device is connected",
                new Color(160, 80, 200));

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

        root.add(buildConnectionArea(), BorderLayout.NORTH);
        root.add(buildLaunchPanel(),    BorderLayout.CENTER);
        root.add(logScroll,             BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(760, 460));
        setLocationRelativeTo(null);

        log("Connect your ESP32 devices to enable hardware players.");
        log("Programs can be launched without hardware — neural players will be skipped.");
    }

    // =========================================================================
    //  Layout helpers
    // =========================================================================

    /** Two connection panels side-by-side, one per device. */
    private JPanel buildConnectionArea() {
        JPanel p = new JPanel(new GridLayout(2, 1, 8, 0));
        p.add(htzPanel);
        p.add(pongPanel);
        return p;
    }

    private JPanel buildLaunchPanel() {
        JPanel p = new JPanel(new GridLayout(1, 3, 12, 0));
        p.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder("Launch Program"),
                new EmptyBorder(8, 8, 8, 8)));

        p.add(wrapLaunchButton(launchBidirectional,
                "HTZ + Pong connections\npassed from Launcher"));
        p.add(wrapLaunchButton(launchHitTheZone,
                "Hardware AI added when\nHTZ device is connected"));
        p.add(wrapLaunchButton(launchPong,
                "Hardware AI enabled when\nPong device is connected"));
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
        JLabel desc = new JLabel(
                "<html><center><small>" + description.replace("\n", "<br>") + "</small></center></html>",
                SwingConstants.CENTER);
        desc.setForeground(Color.DARK_GRAY);
        cell.add(btn,  BorderLayout.CENTER);
        cell.add(desc, BorderLayout.SOUTH);
        return cell;
    }

    // =========================================================================
    //  Program launchers
    // =========================================================================

    /**
     * Opens BidirectionalTest with both Launcher-managed connections.
     * The HTZ manager (3-neuron) and the Pong manager (6-neuron) are passed
     * directly; either may be null if that device is not yet connected.
     * The test window never opens or closes any port — the Launcher retains
     * ownership of both connections.
     */
    private void openBidirectionalTest() {
        log("Launching Bidirectional Test…");
        BidirectionalTest frame = new BidirectionalTest(htzManager, pongManager);
        trackLaunchedWindow(frame, "Bidirectional Test");
        frame.setVisible(true);
    }

    /**
     * Hit The Zone: if the HTZ device is connected, builds a single-channel
     * hardware player (GPIO34, channel 0). Otherwise, launches with software
     * AIs and human player only.
     */
    private void openHitTheZone() {
        log("Launching Hit The Zone…");

        List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players = new ArrayList<>();
        players.add(new HitTheZoneSoftwareAI("Bot Alpha", 3));
        players.add(new HitTheZoneSoftwareAI("Bot Beta",  9));

        if (htzManager != null && htzManager.isConnected()) {
            // 3-neuron config: single channel (GPIO34 = channel 0).
            NeuralSignalParser   parser   = new NeuralSignalParser(0);
            HardwareSignalSource src      = new HardwareSignalSource(htzManager, parser);
            HitTheZoneHardwareAI hwPlayer = new HitTheZoneHardwareAI("Neural", src);
            players.add(hwPlayer);
            log("  → Hardware AI added (HTZ device, ch 0 / GPIO34).");
        } else {
            log("  → HTZ device not connected. Launching without neural player.");
        }

        Map<Integer, HitTheZoneAction> bindings =
                Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE);
        players.add(new HumanPlayer<>("Human", bindings, null));

        PoC_HitTheZone frame = new PoC_HitTheZone(players, htzManager);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        trackLaunchedWindow(frame, "Hit The Zone");
        frame.setVisible(true);
    }

    /**
     * Pong: if the Pong device is connected, builds a single PongHardwareAI
     * from the two ADC channels of the 6-neuron dual-channel firmware.
     *   ch 0 (GPIO34) = LEFT movement
     *   ch 1 (GPIO35) = RIGHT movement
     * Passes it to PongGame; if not connected, passes null so the HARDWARE
     * option in the toolbar is automatically grayed out.
     * Player selection (Human / Hardware / AI Easy / AI Hard) is handled
     * entirely by PongGame and its toolbar UI.
     */
    private void openPong() {
        log("Launching Pong…");

        PongHardwareAI hwPlayer = null;

        if (pongManager != null && pongManager.isConnected() && pongManager.getDeviceChannelCount() >= 2) {
            // 6-neuron config: two ADC channels share the same serial connection.
            NeuralSignalParser   parserL  = new NeuralSignalParser(0); // GPIO34 = LEFT
            NeuralSignalParser   parserR  = new NeuralSignalParser(1); // GPIO35 = RIGHT
            HardwareSignalSource srcLeft  = new HardwareSignalSource(pongManager, parserL);
            HardwareSignalSource srcRight = new HardwareSignalSource(pongManager, parserR);
            hwPlayer = new PongHardwareAI("Hardware", srcLeft, srcRight);
            final PongHardwareAI finalHw = hwPlayer;
            Runtime.getRuntime().addShutdownHook(new Thread(finalHw::close, "pong-hw-close"));
            log("  --> Hardware AI added (ch0=LEFT/GPIO34, ch1=RIGHT/GPIO35).");
        } else if (pongManager != null && pongManager.isConnected()) {
            int ch = pongManager.getDeviceChannelCount();
            log("  --> Pong device has " + ch + " ch (need 2). Launching without neural player.");
        } else {
            log("  --> Pong device not connected. Launching without neural player.");
        }

        PongGame gamePanel = new PongGame(hwPlayer);

        JFrame frame = new JFrame("Pong");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setResizable(false);

        ConnectionStatusPanel pongStatus = new ConnectionStatusPanel();
        pongStatus.addDevice("Pong", pongManager);
        frame.add(pongStatus,  BorderLayout.NORTH);
        frame.add(gamePanel,   BorderLayout.CENTER);
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
     * Registers a window-closed listener that logs when the window is disposed.
     * The window's dispose-on-close is left to the caller.
     */
    private void trackLaunchedWindow(JFrame frame, String name) {
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                log(name + " closed.");
            }
        });
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String ts = LocalTime.now().format(TIME_FMT);
            logArea.append("[" + ts + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
