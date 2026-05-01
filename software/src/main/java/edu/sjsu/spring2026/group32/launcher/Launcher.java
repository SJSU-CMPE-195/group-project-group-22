package edu.sjsu.spring2026.group32.launcher;

import edu.sjsu.spring2026.group32.bidirectionaltest.BidirectionalTest;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hitthezone.HitTheZoneGame;
import edu.sjsu.spring2026.group32.launcher.core.LauncherHardwareMessages;
import edu.sjsu.spring2026.group32.launcher.model.LauncherProgram;
import edu.sjsu.spring2026.group32.launcher.core.LauncherProgramRegistry;
import edu.sjsu.spring2026.group32.launcher.ui.ConnectionSlotsPanel;
import edu.sjsu.spring2026.group32.launcher.ui.LaunchProgramsPanel;
import edu.sjsu.spring2026.group32.launcher.ui.LauncherLogPanel;
import edu.sjsu.spring2026.group32.launcher.ui.SerialConnectionPanel;
import edu.sjsu.spring2026.group32.pong.PongGame;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Set;

/**
 * Central launch hub for all ESP32-backed programs.
 */
public class Launcher extends JFrame {
    private static final int WINDOW_MIN_WIDTH = 820;
    private static final int WINDOW_MIN_HEIGHT = 520;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LauncherProgramRegistry programRegistry = new LauncherProgramRegistry();
    private final SerialConnectionPanel htzPanel;
    private final SerialConnectionPanel pongPanel;
    private final SerialConnectionManager htzManager;
    private final SerialConnectionManager pongManager;
    private final JButton launchBidirectionalButton;
    private final JButton launchHitTheZoneButton;
    private final JButton launchPongButton;
    private final LauncherLogPanel logPanel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Launcher().setVisible(true));
    }

    public Launcher() {
        super("ESP32 Program Launcher");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        htzPanel = buildConnectionPanel(
                "Hit The Zone  -  3-neuron (single channel)",
                1,
                "[HTZ] ",
                "Hit The Zone");
        pongPanel = buildConnectionPanel(
                "Pong  -  6-neuron (dual channel)",
                2,
                "[Pong] ",
                "Pong");

        htzManager = htzPanel.getConnectionManager();
        pongManager = pongPanel.getConnectionManager();
        wireExcludedPorts();

        LaunchProgramsPanel launchProgramsPanel = new LaunchProgramsPanel();
        launchBidirectionalButton = launchProgramsPanel.getLaunchBidirectionalButton();
        launchHitTheZoneButton = launchProgramsPanel.getLaunchHitTheZoneButton();
        launchPongButton = launchProgramsPanel.getLaunchPongButton();
        logPanel = new LauncherLogPanel();

        launchBidirectionalButton.addActionListener(e -> openBidirectionalTest());
        launchHitTheZoneButton.addActionListener(e -> openHitTheZone());
        launchPongButton.addActionListener(e -> openPong());

        setContentPane(buildRootPanel(launchProgramsPanel));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownAndExit();
            }
        });

        pack();
        setMinimumSize(new Dimension(WINDOW_MIN_WIDTH, WINDOW_MIN_HEIGHT));
        refreshLauncherLayout();
        setLocationRelativeTo(null);

        log("Connect your ESP32 devices to enable hardware players.");
        log("Programs can be launched without hardware - neural players will be skipped.");
    }

    private JPanel buildRootPanel(LaunchProgramsPanel launchProgramsPanel) {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel topSection = new JPanel(new BorderLayout(10, 10));
        topSection.setOpaque(false);
        topSection.add(new ConnectionSlotsPanel(htzPanel, pongPanel), BorderLayout.NORTH);
        topSection.add(launchProgramsPanel, BorderLayout.CENTER);

        root.add(topSection, BorderLayout.NORTH);
        root.add(logPanel, BorderLayout.CENTER);
        return root;
    }

    private void wireExcludedPorts() {
        htzPanel.setExcludedPortsSupplier(() -> {
            String port = pongPanel.getConnectedPortName();
            return port != null ? Set.of(port) : Collections.emptySet();
        });
        pongPanel.setExcludedPortsSupplier(() -> {
            String port = htzPanel.getConnectedPortName();
            return port != null ? Set.of(port) : Collections.emptySet();
        });
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
        if (sourcePanel != htzPanel) {
            htzPanel.refreshPorts();
        }
        if (sourcePanel != pongPanel) {
            pongPanel.refreshPorts();
        }
    }

    private void openBidirectionalTest() {
        if (programRegistry.isOpen(LauncherProgram.BIDIRECTIONAL_TEST)) {
            return;
        }

        log("Launching Bidirectional Test...");
        BidirectionalTest frame = new BidirectionalTest(htzManager, pongManager);
        launchBidirectionalButton.setEnabled(false);
        programRegistry.register(LauncherProgram.BIDIRECTIONAL_TEST, frame, () -> {
            launchBidirectionalButton.setEnabled(true);
            log("Bidirectional Test closed.");
        });
        frame.setVisible(true);
    }

    private void openHitTheZone() {
        if (programRegistry.isOpen(LauncherProgram.HIT_THE_ZONE)) {
            return;
        }

        log("Launching Hit The Zone...");
        HitTheZoneGame frame = HitTheZoneGame.launchFromLauncher(htzManager);
        launchHitTheZoneButton.setEnabled(false);
        programRegistry.register(LauncherProgram.HIT_THE_ZONE, frame, () -> {
            launchHitTheZoneButton.setEnabled(true);
            log("Hit The Zone closed.");
        });
        log(LauncherHardwareMessages.describeHitTheZoneLaunch(htzManager));
    }

    private void openPong() {
        if (programRegistry.isOpen(LauncherProgram.PONG)) {
            return;
        }

        log("Launching Pong...");
        JFrame frame = PongGame.launchFromLauncher(pongManager);
        launchPongButton.setEnabled(false);
        programRegistry.register(LauncherProgram.PONG, frame, () -> {
            launchPongButton.setEnabled(true);
            log("Pong closed.");
        });
        log(LauncherHardwareMessages.describePongLaunch(pongManager));
    }

    private void shutdownAndExit() {
        htzPanel.disconnect();
        pongPanel.disconnect();
        dispose();
        System.exit(0);
    }

    private void refreshLauncherLayout() {
        revalidate();
        repaint();
        Dimension preferred = getContentPane().getPreferredSize();
        int minHeight = Math.max(WINDOW_MIN_HEIGHT, preferred.height + 24);
        setMinimumSize(new Dimension(WINDOW_MIN_WIDTH, minHeight));
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalTime.now().format(TIME_FMT);
            logPanel.appendLine("[" + timestamp + "] " + message);
        });
    }
}
