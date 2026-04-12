package edu.sjsu.spring2026.group32;

import edu.sjsu.spring2026.group32.hardware.serial.RealSerialDevice;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.serial.SerialDevice;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test — interactive Swing tool for bidirectional ESP32 ↔ Java serial testing.
 *
 * <p>Left column: dark terminal showing live CSV lines.<br>
 * Right column: scrolling voltage graph (0 – 3.3 V) with optional injection
 * target line.</p>
 *
 * <p>Firmware counterpart: hardware/firmware/Test.ino</p>
 *
 * Protocol (Java → ESP32):
 * <pre>
 *   INJECT_V:&lt;volts&gt;   inject a voltage (0.00 – 3.30 V)
 *   INJECT:&lt;raw&gt;       inject a raw ADC value (0 – 4095)
 *   STOP_INJECT         revert to real ADC readings
 *   STATUS              request a one-line status reply
 * </pre>
 */
public class BidirectionalTest extends JFrame {

    // ── Serial constants ──────────────────────────────────────────────────────
    private static final int    BAUD_RATE = 115200;
    private static final int    MAX_LINES = 500;
    private static final double V_REF     = 3.3;
    private static final int    ADC_MAX   = 4095;

    // ── Mode ──────────────────────────────────────────────────────────────────
    /**
     * {@code true}  → standalone: owns the connection UI and manages its own port.<br>
     * {@code false} → launched: receives an already-open {@link SerialConnectionManager}
     *                 from {@link edu.sjsu.spring2026.group32.launcher.Launcher};
     *                 the connection panel is hidden and the port is not closed on exit.
     */
    private final boolean standaloneMode;

    // ── Serial state ──────────────────────────────────────────────────────────
    private SerialConnectionManager connectionManager;
    private ExecutorService         readerThread;
    private final AtomicBoolean     running = new AtomicBoolean(false);

    // ── Connection panel ──────────────────────────────────────────────────────
    private JComboBox<PortItem> portSelector;
    private JButton             refreshBtn;
    private JButton             connectBtn;
    private JButton             disconnectBtn;
    private JLabel              statusLabel;
    private JLabel              statusDot;
    private JCheckBox           autoFilterCheck;

    /** Substrings (case-insensitive) found in the descriptive name of USB-UART
     *  bridges commonly soldered onto ESP32 development boards. */
    private static final String[] ESP32_BRIDGE_KEYWORDS = {
        "CP210", "CH340", "CH341", "FT232", "FTDI", "ESP32", "ESP8266",
        "Silicon Laboratories", "Silicon Labs"
    };

    // ── Summary strip ─────────────────────────────────────────────────────────
    private JLabel rawLabel;
    private JLabel voltageLabel;
    private JLabel modeLabel;

    // ── Terminal ──────────────────────────────────────────────────────────────
    private JTextArea        dataDisplay;
    private JButton          pauseBtn;
    private volatile boolean displayPaused = false;

    // ── Graph ─────────────────────────────────────────────────────────────────
    private VoltageGraph voltageGraph;

    // ── Injection panel ───────────────────────────────────────────────────────
    private JTextField   voltageField;
    private JSlider      voltageSlider;
    private JButton      injectBtn;
    private JButton      stopInjectBtn;
    private JLabel       sliderValueLabel;
    private JRadioButton continuousRadio;
    private JRadioButton intervalRadio;
    private JSpinner     onSpinner;          // pulse on-time  (ms)
    private JSpinner     offSpinner;         // gap between pulses (ms)
    private JSpinner     repeatSpinner;      // repeat count
    private JCheckBox    infiniteCheck;      // repeat indefinitely
    private JPanel       intervalOptsPanel;  // shown only in interval mode

    // ── Injection scheduler ───────────────────────────────────────────────────
    private final ScheduledExecutorService injScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "InjScheduler");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> pendingTask;
    private int    remainingRepeats  = 0;
    private double currentInjectionV = 0.0;

    // ── Timestamp formatter ───────────────────────────────────────────────────
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // =========================================================================
    //  Entry point
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BidirectionalTest().setVisible(true));
    }

    // =========================================================================
    //  Constructors
    // =========================================================================

    /**
     * Standalone constructor — shows the full Serial Connection panel so the
     * user can pick a COM port directly from this window.
     * Use this when running {@code BidirectionalTest} as its own entry point.
     */
    public BidirectionalTest() {
        super("ESP32 ↔ Java Serial Test");
        this.standaloneMode = true;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                disconnect();
                dispose();
            }
        });

        buildUI();
        refreshPorts();

        setMinimumSize(new Dimension(1000, 600));
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Launcher-injected constructor.  The COM port is already open; this window
     * hides the connection panel and does <em>not</em> close the port when
     * disposed — the {@link edu.sjsu.spring2026.group32.launcher.Launcher}
     * retains ownership.
     *
     * @param connectionManager an already-connected serial manager provided
     *                          by the Launcher
     */
    public BidirectionalTest(SerialConnectionManager connectionManager) {
        super("ESP32 ↔ Java Serial Test  [via Launcher]");
        this.standaloneMode    = false;
        this.connectionManager = connectionManager;

        // DISPOSE, not EXIT — the Launcher must keep running.
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                // Stop reading but do NOT close the port — the Launcher owns it.
                stopReadLoop();
                appendLog("── Closed (port stays open in Launcher) ──");
            }
        });

        buildUI();

        // Already connected: reset graph, silence injection, start reading.
        voltageGraph.reset();
        voltageGraph.setInjection(false, 0.0);
        connectionManager.sendLine("STOP_INJECT");
        startReadLoop();
        injectBtn.setEnabled(true);
        stopInjectBtn.setEnabled(true);
        appendLog("── Connected (launched from Launcher) ──");
        appendLog("Injection off by default.");

        setMinimumSize(new Dimension(1000, 600));
        pack();
        setLocationRelativeTo(null);
    }

    // =========================================================================
    //  UI construction
    // =========================================================================
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(root);

        // The connection panel is only shown in standalone mode.
        // When launched from Launcher, the port is already open and the
        // Launcher's SerialConnectionPanel owns the connection UI.
        if (standaloneMode) root.add(buildConnectionPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(),     BorderLayout.CENTER);
        root.add(buildInjectionPanel(),  BorderLayout.SOUTH);
    }

    // ── Connection panel ──────────────────────────────────────────────────────
    private JPanel buildConnectionPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Serial Connection"));

        portSelector  = new JComboBox<>();
        portSelector.setPreferredSize(new Dimension(340, 26));

        refreshBtn    = new JButton("↺  Refresh");
        connectBtn    = new JButton("Connect");
        disconnectBtn = new JButton("Disconnect");
        disconnectBtn.setEnabled(false);

        statusDot   = new JLabel("●");
        statusDot.setForeground(Color.RED);
        statusLabel = new JLabel("Disconnected");

        connectBtn.setBackground(new Color(70, 160, 70));
        connectBtn.setForeground(Color.WHITE);
        connectBtn.setOpaque(true);

        disconnectBtn.setBackground(new Color(190, 60, 60));
        disconnectBtn.setForeground(Color.WHITE);
        disconnectBtn.setOpaque(true);

        autoFilterCheck = new JCheckBox("ESP32 only", true);
        autoFilterCheck.setToolTipText(
                "When checked, only shows ports whose device name matches a known ESP32 USB-UART bridge");
        autoFilterCheck.addItemListener(e -> refreshPorts());

        p.add(new JLabel("COM Port:"));
        p.add(portSelector);
        p.add(autoFilterCheck);
        p.add(refreshBtn);
        p.add(connectBtn);
        p.add(disconnectBtn);
        p.add(Box.createHorizontalStrut(12));
        p.add(statusDot);
        p.add(statusLabel);

        refreshBtn.addActionListener(e -> refreshPorts());
        connectBtn.addActionListener(e -> connect());
        disconnectBtn.addActionListener(e -> disconnect());

        return p;
    }

    // ── Center: two-column split (terminal | graph) ───────────────────────────
    private JPanel buildCenterPanel() {

        // ── Left column: summary strip + dark terminal ──────────────────────
        JPanel leftCol = new JPanel(new BorderLayout(4, 4));

        JPanel strip = new JPanel(new BorderLayout());
        strip.setBorder(new TitledBorder("Live Readings"));

        JPanel labelsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        rawLabel     = makeSummaryLabel("Raw ADC: —");
        voltageLabel = makeSummaryLabel("Voltage: —");
        modeLabel    = makeSummaryLabel("Mode: —");
        labelsPanel.add(rawLabel);
        labelsPanel.add(voltageLabel);
        labelsPanel.add(modeLabel);

        pauseBtn = new JButton("⏸ Pause");
        pauseBtn.setToolTipText("Pause terminal output (graph and labels keep updating)");
        pauseBtn.addActionListener(e -> togglePause());

        strip.add(labelsPanel, BorderLayout.CENTER);
        strip.add(pauseBtn,    BorderLayout.EAST);
        leftCol.add(strip, BorderLayout.NORTH);

        dataDisplay = new JTextArea();
        dataDisplay.setEditable(false);
        dataDisplay.setFont(new Font("Monospaced", Font.PLAIN, 12));
        dataDisplay.setBackground(new Color(18, 18, 18));
        dataDisplay.setForeground(new Color(200, 230, 200));
        dataDisplay.setCaretColor(Color.GREEN);

        JScrollPane scroll = new JScrollPane(dataDisplay);
        scroll.setPreferredSize(new Dimension(480, 380));
        leftCol.add(scroll, BorderLayout.CENTER);

        // ── Right column: voltage graph ──────────────────────────────────────
        voltageGraph = new VoltageGraph();
        voltageGraph.setBorder(new TitledBorder("Voltage (V)"));
        voltageGraph.setPreferredSize(new Dimension(420, 420));

        // ── Split pane ───────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCol, voltageGraph);
        split.setResizeWeight(0.52);
        split.setDividerLocation(500);
        split.setDividerSize(5);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(split, BorderLayout.CENTER);
        return wrapper;
    }

    private JLabel makeSummaryLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 13));
        return lbl;
    }

    // ── Injection panel ───────────────────────────────────────────────────────
    private JPanel buildInjectionPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 4));
        p.setBorder(new TitledBorder("Voltage Injection"));

        // ── Slider row ───────────────────────────────────────────────────────
        JPanel sliderRow = new JPanel(new BorderLayout(6, 0));
        sliderRow.setBorder(new EmptyBorder(2, 4, 2, 4));

        voltageSlider    = new JSlider(0, 330, 0);
        sliderValueLabel = new JLabel("0.00 V");
        sliderValueLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        sliderValueLabel.setPreferredSize(new Dimension(56, 20));

        voltageSlider.addChangeListener(e -> {
            double v = voltageSlider.getValue() / 100.0;
            sliderValueLabel.setText(String.format("%.2f V", v));
            voltageField.setText(String.format("%.2f", v));
        });

        sliderRow.add(new JLabel("0.00 V"), BorderLayout.WEST);
        sliderRow.add(voltageSlider,        BorderLayout.CENTER);
        sliderRow.add(new JLabel("3.30 V"), BorderLayout.EAST);

        // ── Controls row (voltage field + buttons) ───────────────────────────
        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        voltageField = new JTextField("0.00", 7);
        voltageField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        voltageField.addActionListener(e -> syncSliderFromField());

        injectBtn     = new JButton("▶  Inject");
        stopInjectBtn = new JButton("■  Stop");

        injectBtn.setEnabled(false);
        stopInjectBtn.setEnabled(false);

        injectBtn.setBackground(new Color(60, 120, 200));
        injectBtn.setForeground(Color.WHITE);
        injectBtn.setOpaque(true);

        stopInjectBtn.setBackground(new Color(190, 100, 30));
        stopInjectBtn.setForeground(Color.WHITE);
        stopInjectBtn.setOpaque(true);

        controlRow.add(new JLabel("Voltage (V):"));
        controlRow.add(voltageField);
        controlRow.add(sliderValueLabel);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(injectBtn);
        controlRow.add(stopInjectBtn);

        // ── Mode row (Continuous / Interval) ─────────────────────────────────
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        continuousRadio = new JRadioButton("Continuous");
        continuousRadio.setToolTipText("Inject and hold until Stop is pressed");
        continuousRadio.setSelected(true);

        intervalRadio = new JRadioButton("Interval");
        intervalRadio.setToolTipText("Pulse on/off repeatedly with configurable on-time and off-time");

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(continuousRadio);
        modeGroup.add(intervalRadio);

        // Interval options — shown only when interval mode is active
        onSpinner  = new JSpinner(new SpinnerNumberModel(200, 10, 60000, 50));
        offSpinner = new JSpinner(new SpinnerNumberModel(300, 10, 60000, 50));
        onSpinner .setPreferredSize(new Dimension(72, 26));
        offSpinner.setPreferredSize(new Dimension(72, 26));

        repeatSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 9999, 1));
        repeatSpinner.setPreferredSize(new Dimension(60, 26));

        infiniteCheck = new JCheckBox("∞");
        infiniteCheck.setToolTipText("Repeat indefinitely until Stop is pressed");

        // ∞ disables the repeat count spinner
        infiniteCheck.addItemListener(e ->
                repeatSpinner.setEnabled(!infiniteCheck.isSelected()));

        intervalOptsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        intervalOptsPanel.add(new JLabel("On:"));
        intervalOptsPanel.add(onSpinner);
        intervalOptsPanel.add(new JLabel("ms   Off:"));
        intervalOptsPanel.add(offSpinner);
        intervalOptsPanel.add(new JLabel("ms   Repeat:"));
        intervalOptsPanel.add(repeatSpinner);
        intervalOptsPanel.add(new JLabel("×"));
        intervalOptsPanel.add(infiniteCheck);
        intervalOptsPanel.setVisible(false);

        // Toggle interval options visibility when mode changes
        intervalRadio.addItemListener(e -> {
            boolean iv = intervalRadio.isSelected();
            intervalOptsPanel.setVisible(iv);
            repeatSpinner.setEnabled(iv && !infiniteCheck.isSelected());
        });

        modeRow.add(new JLabel("Mode:"));
        modeRow.add(continuousRadio);
        modeRow.add(intervalRadio);
        modeRow.add(intervalOptsPanel);

        JPanel inner = new JPanel(new BorderLayout(0, 2));
        inner.add(sliderRow,  BorderLayout.NORTH);
        inner.add(controlRow, BorderLayout.CENTER);
        inner.add(modeRow,    BorderLayout.SOUTH);
        p.add(inner, BorderLayout.CENTER);

        injectBtn.addActionListener(e -> injectVoltage());
        stopInjectBtn.addActionListener(e -> stopInjection());

        return p;
    }

    // =========================================================================
    //  Port management
    // =========================================================================
    private void refreshPorts() {
        // Remember which port is currently selected so we can restore it.
        PortItem prev = (PortItem) portSelector.getSelectedItem();
        String prevName = (prev != null && !prev.isPlaceholder())
                ? prev.device.getSystemPortName() : null;

        portSelector.removeAllItems();
        portSelector.addItem(new PortItem(null));   // blank placeholder always first

        SerialDevice[] all = RealSerialDevice.getRealPorts();
        boolean filter = autoFilterCheck != null && autoFilterCheck.isSelected();

        int shown = 0;
        int reselect = -1;   // combo index to restore
        for (SerialDevice d : all) {
            if (filter && !looksLikeEsp32Bridge(d)) continue;
            portSelector.addItem(new PortItem(d));
            shown++;
            // +1 because index 0 is the placeholder
            if (d.getSystemPortName().equals(prevName)) reselect = shown;
        }

        if (reselect >= 0) {
            portSelector.setSelectedIndex(reselect);   // restore previous selection
        }
        // else: leave the placeholder selected (index 0)

        if (shown == 0) {
            if (filter && all.length > 0) {
                appendLog(String.format(
                        "No ESP32 ports found (%d other port(s) hidden by filter). " +
                        "Uncheck 'ESP32 only' to see all.", all.length));
            } else {
                appendLog("No serial ports found. Plug in the ESP32 and press Refresh.");
            }
        } else {
            String filterNote = filter ? " (ESP32 filter on)" : "";
            appendLog(String.format("Found %d port(s)%s. Select your ESP32 and press Connect.",
                    shown, filterNote));
        }
    }

    /** Returns true if the port's descriptive name contains a keyword associated
     *  with a USB-UART bridge chip used on ESP32 dev boards. */
    private static boolean looksLikeEsp32Bridge(SerialDevice d) {
        String name = d.getDescriptivePortName().toUpperCase();
        for (String kw : ESP32_BRIDGE_KEYWORDS) {
            if (name.contains(kw.toUpperCase())) return true;
        }
        return false;
    }

    // =========================================================================
    //  Connect / Disconnect
    // =========================================================================
    private void connect() {
        PortItem item = (PortItem) portSelector.getSelectedItem();
        if (item == null || item.device == null) {
            JOptionPane.showMessageDialog(this,
                    "No port selected. Press Refresh and choose your ESP32.",
                    "No Port", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 2000 ms semi-blocking: return as soon as ≥1 byte arrives, or after 2 s.
        // The ESP32 sends every ~10 ms, so 2000 ms only fires on genuine silence.
        connectionManager = new SerialConnectionManager(RealSerialDevice::getRealPorts, 2000);

        if (!connectionManager.connectTo(item.device)) {
            JOptionPane.showMessageDialog(this,
                    "Could not open " + item.device.getSystemPortName() +
                    ".\nIs another application using it?",
                    "Connection Failed", JOptionPane.ERROR_MESSAGE);
            connectionManager = null;
            return;
        }

        // Ensure the ESP32 starts in real-ADC mode regardless of prior session state.
        connectionManager.sendLine("STOP_INJECT");

        voltageGraph.reset();
        voltageGraph.setInjection(false, 0.0);

        startReadLoop();
        setConnectedState(true, item.toString());
        appendLog("── Connected: " + item + " ──");
        appendLog("Injection off by default.");
    }

    /**
     * Stops the serial read thread without closing the underlying port.
     * Used by the launched-mode window listener so the Launcher retains
     * ownership of the connection.
     */
    private void stopReadLoop() {
        if (!running.getAndSet(false)) return;
        cancelPendingTask();
        if (readerThread != null) {
            readerThread.shutdownNow();
            readerThread = null;
        }
    }

    /** Full disconnect — stops the read loop AND closes the port. Standalone only. */
    private void disconnect() {
        stopReadLoop();

        if (connectionManager != null) {
            connectionManager.disconnect();
            connectionManager = null;
        }

        setConnectedState(false, null);
        appendLog("── Disconnected ──");
    }

    private void setConnectedState(boolean connected, String portLabel) {
        SwingUtilities.invokeLater(() -> {
            // These UI components only exist in standalone mode.
            if (standaloneMode) {
                connectBtn   .setEnabled(!connected);
                disconnectBtn.setEnabled( connected);
                refreshBtn   .setEnabled(!connected);
                portSelector .setEnabled(!connected);

                if (connected) {
                    statusDot.setForeground(new Color(40, 190, 40));
                    statusLabel.setText("Connected: " + portLabel);
                } else {
                    statusDot.setForeground(Color.RED);
                    statusLabel.setText("Disconnected");
                }
            }

            injectBtn    .setEnabled(connected);
            stopInjectBtn.setEnabled(connected);

            if (!connected) {
                rawLabel    .setText("Raw ADC: —");
                voltageLabel.setText("Voltage: —");
                modeLabel   .setText("Mode: —");
            }
        });
    }

    // =========================================================================
    //  Serial read loop
    // =========================================================================
    private void startReadLoop() {
        running.set(true);
        readerThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ESP32-Reader");
            t.setDaemon(true);
            return t;
        });

        readerThread.execute(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connectionManager.getInputStream()))) {

                while (running.get()) {
                    try {
                        String line = reader.readLine();
                        if (line != null && !line.isBlank()) {
                            handleIncomingLine(line.trim());
                        }
                    } catch (java.io.IOException readEx) {
                        // An IOException here is usually a semi-blocking timeout
                        // (no bytes arrived within the 2 s window) rather than a
                        // physical disconnection.  Check the port first.
                        if (!running.get()) {
                            break;  // deliberate disconnect() was called — exit quietly
                        }
                        if (connectionManager != null && connectionManager.isConnected()) {
                            // Port is still alive — just a quiet moment; keep looping.
                            continue;
                        }
                        // Port has actually closed — report and clean up.
                        final String msg = readEx.getMessage();
                        SwingUtilities.invokeLater(() -> {
                            appendLog("Connection lost: " + msg);
                            disconnect();
                        });
                        break;
                    }
                }
            } catch (Exception outerEx) {
                // Covers failures opening the InputStream itself.
                if (running.get()) {
                    final String msg = outerEx.getMessage();
                    SwingUtilities.invokeLater(() -> {
                        appendLog("Reader error: " + msg);
                        disconnect();
                    });
                }
            }
        });
    }

    // =========================================================================
    //  Incoming line parser
    // =========================================================================
    private void handleIncomingLine(String line) {
        if (line.startsWith("STATUS,")) {
            String payload = line.substring(7);
            SwingUtilities.invokeLater(() -> {
                appendLog("Status: " + payload);
                updateModeAndGraph(payload);
            });
            return;
        }

        // Normal CSV: millis,1,0,rawADC
        String[] parts = line.split(",");
        if (parts.length >= 4) {
            try {
                long   ms      = Long.parseLong(parts[0].trim());
                int    raw     = Integer.parseInt(parts[3].trim());
                double voltage = (raw / (double) ADC_MAX) * V_REF;

                final String timeStr  = LocalTime.now().format(TIME_FMT);
                String formatted = String.format(
                        "%s  %5.3f V  (raw %4d, t=%dms)",
                        timeStr, voltage, raw, ms);

                final double v = voltage;
                SwingUtilities.invokeLater(() -> {
                    if (!displayPaused) appendLog(formatted);
                    rawLabel.setText("Raw ADC: " + raw);
                    voltageLabel.setText(String.format("Voltage: %.3f V", v));
                    voltageGraph.addSample(v, raw, ms, timeStr);
                });
                return;
            } catch (NumberFormatException ignored) {
                // fall through to raw display
            }
        }

        // Unrecognised line — display as-is
        final String raw = line;
        SwingUtilities.invokeLater(() -> appendLog("[RAW] " + raw));
    }

    private void updateModeAndGraph(String statusPayload) {
        if (statusPayload.contains("INJECT_START")) {
            modeLabel.setText("Mode: INJECTING");
            modeLabel.setForeground(new Color(220, 120, 0));
            // Parse injected voltage from "INJECT_START,<raw>,<v>V"
            String[] parts = statusPayload.split(",");
            if (parts.length >= 3) {
                try {
                    String vStr = parts[2].replace("V", "").trim();
                    voltageGraph.setInjection(true, Double.parseDouble(vStr));
                } catch (NumberFormatException ignored) {
                    voltageGraph.setInjection(true, 0.0);
                }
            }
        } else if (statusPayload.contains("INJECT_STOPPED") || statusPayload.contains("NORMAL")) {
            modeLabel.setText("Mode: NORMAL");
            modeLabel.setForeground(new Color(40, 160, 40));
            voltageGraph.setInjection(false, 0.0);
        }
    }

    // =========================================================================
    //  Log area helpers
    // =========================================================================
    private void togglePause() {
        displayPaused = !displayPaused;
        if (displayPaused) {
            pauseBtn.setText("▶ Resume");
            pauseBtn.setToolTipText("Resume terminal output");
            appendLog("── Output paused ──");
        } else {
            pauseBtn.setText("⏸ Pause");
            pauseBtn.setToolTipText("Pause terminal output (graph and labels keep updating)");
            appendLog("── Output resumed ──");
        }
    }

    private void appendLog(String text) {
        dataDisplay.append(text + "\n");
        trimLog();
        dataDisplay.setCaretPosition(dataDisplay.getDocument().getLength());
    }

    private void trimLog() {
        String content = dataDisplay.getText();
        int newlines = 0;
        int cutIndex = -1;
        for (int i = content.length() - 1; i >= 0; i--) {
            if (content.charAt(i) == '\n') {
                newlines++;
                if (newlines >= MAX_LINES) {
                    cutIndex = i + 1;
                    break;
                }
            }
        }
        if (cutIndex > 0) {
            dataDisplay.setText(content.substring(cutIndex));
        }
    }

    // =========================================================================
    //  Injection commands
    // =========================================================================
    private void injectVoltage() {
        syncSliderFromField();
        String raw = voltageField.getText().trim();
        double v;
        try {
            v = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Enter a number between 0.00 and 3.30.",
                    "Invalid Input", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (v < 0.0 || v > V_REF) {
            JOptionPane.showMessageDialog(this,
                    String.format("Voltage must be 0.00 – %.2f V.", V_REF),
                    "Out of Range", JOptionPane.WARNING_MESSAGE);
            return;
        }

        cancelPendingTask();
        currentInjectionV = v;

        if (intervalRadio.isSelected()) {
            remainingRepeats = infiniteCheck.isSelected()
                    ? Integer.MAX_VALUE
                    : (int) repeatSpinner.getValue();
            int onMs  = (int) onSpinner.getValue();
            int offMs = (int) offSpinner.getValue();
            appendLog(String.format("Interval injection: %.3f V  on=%dms  off=%dms  ×%s",
                    v, onMs, offMs,
                    infiniteCheck.isSelected() ? "∞" : repeatSpinner.getValue()));
        }

        startInjectionCycle();
    }

    /** Sends INJECT_V and — in interval mode — schedules the pulse-end timer. */
    private void startInjectionCycle() {
        sendCommand(String.format("INJECT_V:%.3f", currentInjectionV));
        voltageGraph.setInjection(true, currentInjectionV);

        if (continuousRadio.isSelected()) return; // hold until Stop is pressed

        int onMs = (int) onSpinner.getValue();
        pendingTask = injScheduler.schedule(
                () -> SwingUtilities.invokeLater(this::onPulseEnd),
                onMs, TimeUnit.MILLISECONDS);
    }

    /** Called when the on-time expires; sends STOP and schedules the next pulse. */
    private void onPulseEnd() {
        sendCommand("STOP_INJECT");
        voltageGraph.setInjection(false, 0.0);

        if (remainingRepeats <= 1) {
            remainingRepeats = 0;
            return;
        }
        if (remainingRepeats != Integer.MAX_VALUE) remainingRepeats--;

        int offMs = (int) offSpinner.getValue();
        pendingTask = injScheduler.schedule(
                () -> SwingUtilities.invokeLater(this::startInjectionCycle),
                offMs, TimeUnit.MILLISECONDS);
    }

    private void stopInjection() {
        cancelPendingTask();
        remainingRepeats = 0;
        sendCommand("STOP_INJECT");
        voltageGraph.setInjection(false, 0.0);
    }

    private void cancelPendingTask() {
        if (pendingTask != null && !pendingTask.isDone()) {
            pendingTask.cancel(false);
        }
        pendingTask = null;
    }

    private void sendCommand(String cmd) {
        if (connectionManager == null || !running.get()) {
            appendLog("Not connected — command not sent.");
            return;
        }
        connectionManager.sendLine(cmd);
        appendLog("→ " + cmd);
    }

    // =========================================================================
    //  Slider ↔ Text field sync
    // =========================================================================
    private void syncSliderFromField() {
        try {
            double v = Double.parseDouble(voltageField.getText().trim());
            v = Math.max(0.0, Math.min(V_REF, v));
            voltageSlider.setValue((int) Math.round(v * 100));
            sliderValueLabel.setText(String.format("%.2f V", v));
        } catch (NumberFormatException ignored) { }
    }

    // =========================================================================
    //  VoltageGraph — custom scrolling chart panel
    // =========================================================================
    private static class VoltageGraph extends JPanel {

        private static final int   BUFFER   = 500;
        private static final double V_REF   = 3.3;
        private static final int   ML = 44, MR = 10, MT = 12, MB = 26;

        private static final Color BG       = new Color(18,  18,  18);
        private static final Color GRID_COL = new Color(45,  45,  45);
        private static final Color TRACE    = new Color(50,  220, 80);
        private static final Color INJECT_C = new Color(230, 140, 30);
        private static final Color AXIS_TXT = new Color(150, 150, 150);
        private static final Color HOVER_C  = new Color(255, 255, 255, 160);
        private static final Color TIP_BG   = new Color(30,  30,  30,  220);

        // ── Ring buffer (parallel arrays) ─────────────────────────────────────
        private final double[] vBuf   = new double[BUFFER];
        private final int[]    rawBuf = new int   [BUFFER];
        private final long[]   msBuf  = new long  [BUFFER];
        private final String[] tBuf   = new String[BUFFER];
        private int head  = 0;
        private int count = 0;

        // ── State ─────────────────────────────────────────────────────────────
        private boolean injecting  = false;
        private double  injectionV = 0.0;
        private boolean paused     = false;
        private int     hoverX     = -1;

        // ── Pause button (overlay) ────────────────────────────────────────────
        private final JButton pauseBtn;

        VoltageGraph() {
            setLayout(null);   // absolute positioning for the overlay button

            pauseBtn = new JButton("⏸");
            pauseBtn.setToolTipText("Pause graph updates");
            pauseBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            pauseBtn.setMargin(new Insets(1, 5, 1, 5));
            pauseBtn.setFocusPainted(false);
            pauseBtn.addActionListener(e -> {
                paused = !paused;
                pauseBtn.setText(paused ? "▶" : "⏸");
                pauseBtn.setToolTipText(paused ? "Resume graph" : "Pause graph");
                repaint();
            });
            add(pauseBtn);

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    hoverX = e.getX();
                    repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    hoverX = -1;
                    repaint();
                }
            });
        }

        void addSample(double v, int raw, long ms, String time) {
            if (paused) return;
            vBuf  [head] = v;
            rawBuf[head] = raw;
            msBuf [head] = ms;
            tBuf  [head] = time;
            head = (head + 1) % BUFFER;
            if (count < BUFFER) count++;
            repaint();
        }

        void setInjection(boolean active, double v) {
            injecting  = active;
            injectionV = v;
            repaint();
        }

        void reset() {
            head  = 0;
            count = 0;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w  = getWidth();
            int h  = getHeight();
            int pw = w - ML - MR;
            int ph = h - MT - MB;

            // Position pause button top-right of the plot area
            Dimension btnSz = pauseBtn.getPreferredSize();
            pauseBtn.setBounds(w - btnSz.width - MR, MT, btnSz.width, btnSz.height);

            // ── Background ───────────────────────────────────────────────────
            g2.setColor(BG);
            g2.fillRect(0, 0, w, h);

            if (pw <= 0 || ph <= 0) { g2.dispose(); return; }

            // ── Grid + Y-axis labels ──────────────────────────────────────────
            double[] gridV = {0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.3};
            g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
            FontMetrics fm = g2.getFontMetrics();
            for (double gv : gridV) {
                int y = yPx(gv, ph);
                g2.setColor(GRID_COL);
                g2.drawLine(ML, MT + y, ML + pw, MT + y);
                String lbl = String.format("%.1f", gv);
                g2.setColor(AXIS_TXT);
                g2.drawString(lbl, ML - fm.stringWidth(lbl) - 3, MT + y + 4);
            }

            // ── Injection target line (dashed orange) ─────────────────────────
            if (injecting) {
                int iy = MT + yPx(injectionV, ph);
                g2.setColor(INJECT_C);
                float[] dash = {7f, 4f};
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10f, dash, 0f));
                g2.drawLine(ML, iy, ML + pw, iy);
                g2.setStroke(new BasicStroke(1f));
                g2.drawString(String.format("inject %.2fV", injectionV), ML + 4, iy - 3);
            }

            // ── Voltage trace ─────────────────────────────────────────────────
            int hoverIdx = -1;
            double hoverV = 0.0;
            int hoverPxSnapped = hoverX;

            if (count >= 2) {
                int pts    = Math.min(count, pw);
                int oldest = (head - pts + BUFFER) % BUFFER;

                g2.setColor(TRACE);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int prevX = -1, prevY = -1;
                for (int i = 0; i < pts; i++) {
                    int    idx = (oldest + i) % BUFFER;
                    double v   = Math.max(0.0, Math.min(V_REF, vBuf[idx]));
                    int    x   = ML + (int) Math.round((double) i / (pts - 1) * pw);
                    int    y   = MT + yPx(v, ph);

                    // Find the sample closest to the cursor
                    if (hoverX >= ML && hoverX <= ML + pw) {
                        if (hoverIdx < 0 || Math.abs(x - hoverX) < Math.abs(hoverPxSnapped - hoverX)) {
                            hoverIdx      = idx;
                            hoverV        = v;
                            hoverPxSnapped = x;
                        }
                    }

                    if (prevX >= 0) g2.drawLine(prevX, prevY, x, y);
                    prevX = x;
                    prevY = y;
                }
            } else {
                g2.setColor(AXIS_TXT);
                g2.setFont(new Font("SansSerif", Font.ITALIC, 12));
                String msg = "Waiting for data…";
                FontMetrics sfm = g2.getFontMetrics();
                g2.drawString(msg, ML + (pw - sfm.stringWidth(msg)) / 2, MT + ph / 2);
            }

            // ── Paused banner ─────────────────────────────────────────────────
            if (paused) {
                g2.setColor(new Color(255, 200, 0, 55));
                g2.fillRect(ML, MT, pw, ph);
                g2.setColor(new Color(255, 200, 0, 180));
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                String msg = "PAUSED";
                FontMetrics sfm = g2.getFontMetrics();
                g2.drawString(msg, ML + (pw - sfm.stringWidth(msg)) / 2, MT + ph / 2);
            }

            // ── Hover crosshair + tooltip ─────────────────────────────────────
            if (hoverIdx >= 0) {
                int dotY = MT + yPx(hoverV, ph);

                // Vertical line
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(HOVER_C);
                g2.drawLine(hoverPxSnapped, MT, hoverPxSnapped, MT + ph);

                // Dot at data point
                g2.setColor(Color.WHITE);
                g2.fillOval(hoverPxSnapped - 4, dotY - 4, 8, 8);
                g2.setColor(TRACE);
                g2.fillOval(hoverPxSnapped - 2, dotY - 2, 5, 5);

                // Tooltip box
                String l1 = tBuf[hoverIdx] != null ? tBuf[hoverIdx] : "—";
                String l2 = String.format("%.3f V", hoverV);
                String l3 = String.format("raw  %d", rawBuf[hoverIdx]);
                String l4 = String.format("t = %d ms", msBuf[hoverIdx]);

                g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
                FontMetrics tfm = g2.getFontMetrics();
                int tw = Math.max(Math.max(tfm.stringWidth(l1), tfm.stringWidth(l2)),
                                  Math.max(tfm.stringWidth(l3), tfm.stringWidth(l4))) + 14;
                int lh = tfm.getHeight();
                int th = lh * 4 + 10;

                int tx = hoverPxSnapped + 10;
                int ty = dotY - th / 2;
                if (tx + tw > w - MR)         tx = hoverPxSnapped - tw - 10;
                if (ty < MT + 2)              ty = MT + 2;
                if (ty + th > MT + ph - 2)   ty = MT + ph - th - 2;

                g2.setColor(TIP_BG);
                g2.fillRoundRect(tx, ty, tw, th, 7, 7);
                g2.setColor(HOVER_C);
                g2.drawRoundRect(tx, ty, tw, th, 7, 7);

                g2.setColor(Color.WHITE);
                g2.drawString(l1, tx + 7, ty + lh);
                g2.setColor(new Color(80, 240, 110));
                g2.drawString(l2, tx + 7, ty + lh * 2);
                g2.setColor(AXIS_TXT);
                g2.drawString(l3, tx + 7, ty + lh * 3);
                g2.drawString(l4, tx + 7, ty + lh * 4);
            }

            // ── Plot border ───────────────────────────────────────────────────
            g2.setColor(AXIS_TXT);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(ML, MT, pw, ph);

            // ── X-axis label ──────────────────────────────────────────────────
            g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
            String xLabel = "← time  (newest on right, last " + BUFFER + " samples)";
            g2.drawString(xLabel, ML + 4, h - 7);

            g2.dispose();
        }

        private static int yPx(double v, int plotH) {
            return plotH - (int) Math.round(Math.min(v, V_REF) / V_REF * plotH);
        }
    }

    // =========================================================================
        //  PortItem — combo box display model
        // =========================================================================
        private record PortItem(SerialDevice device) {

        boolean isPlaceholder() {
            return device == null;
        }

            @Override
            public String toString() {
                if (device == null) return "---";
                return device.getSystemPortName() + "  —  " + device.getDescriptivePortName();
            }
        }
}