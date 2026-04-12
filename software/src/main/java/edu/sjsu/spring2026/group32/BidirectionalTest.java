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
    private JLabel    rawLabel;
    private JLabel    voltageLabel;
    private JLabel    ch2RawLabel;
    private JLabel    ch2VoltageLabel;
    private JLabel    modeLabel;

    // ── Channel checkboxes (control which traces the graph shows) ─────────────
    private JCheckBox ch1Check;
    private JCheckBox ch2Check;

    // ── Terminal ──────────────────────────────────────────────────────────────
    private JTextArea        dataDisplay;
    private JButton          pauseBtn;
    private volatile boolean displayPaused = false;

    // ── Graph ─────────────────────────────────────────────────────────────────
    private VoltageGraph voltageGraph;

    // ── Injection panel ───────────────────────────────────────────────────────
    private JTextField        voltageField;
    private JSlider           voltageSlider;
    private JButton           injectBtn;
    private JButton           stopInjectBtn;
    private JLabel            sliderValueLabel;
    private JComboBox<String> injectChannelCombo;
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
    private int    remainingRepeats     = 0;
    private double currentInjectionV   = 0.0;
    private int    currentInjectionCh  = 0;   // 0 = Ch1, 1 = Ch2

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
        voltageGraph.setInjection(0, false, 0.0);
        voltageGraph.setInjection(1, false, 0.0);
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

        JPanel labelsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        rawLabel        = makeSummaryLabel("Ch1 ADC: —");
        voltageLabel    = makeSummaryLabel("Ch1: — V");
        ch2RawLabel     = makeSummaryLabel("Ch2 ADC: —");
        ch2VoltageLabel = makeSummaryLabel("Ch2: — V");
        modeLabel       = makeSummaryLabel("Mode: —");

        // Ch2 labels are hidden until dual-channel data arrives.
        ch2RawLabel    .setVisible(false);
        ch2VoltageLabel.setVisible(false);

        // ── Channel checkboxes ────────────────────────────────────────────────
        ch1Check = new JCheckBox("Ch 1", true);
        ch2Check = new JCheckBox("Ch 2", false);
        ch1Check.setFont(new Font("SansSerif", Font.PLAIN, 11));
        ch2Check.setFont(new Font("SansSerif", Font.PLAIN, 11));
        ch1Check.setForeground(new Color(50, 220, 80));   // green — matches graph trace
        ch2Check.setForeground(new Color(80, 180, 255));  // blue  — matches graph trace
        ch1Check.setToolTipText("Show / hide Channel 1 trace (GPIO34)");
        ch2Check.setToolTipText("Show / hide Channel 2 trace (GPIO35, dual-channel firmware only)");
        // Wire checkboxes to graph visibility — voltageGraph is assigned in buildCenterPanel
        // so the listeners are attached after the graph exists (buildUI order is safe).
        ch1Check.addItemListener(e -> voltageGraph.setChannelVisible(0, ch1Check.isSelected()));
        ch2Check.addItemListener(e -> voltageGraph.setChannelVisible(1, ch2Check.isSelected()));

        labelsPanel.add(rawLabel);
        labelsPanel.add(voltageLabel);
        labelsPanel.add(ch2RawLabel);
        labelsPanel.add(ch2VoltageLabel);
        labelsPanel.add(modeLabel);
        labelsPanel.add(Box.createHorizontalStrut(12));
        labelsPanel.add(new JLabel("Graph:"));
        labelsPanel.add(ch1Check);
        labelsPanel.add(ch2Check);

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

        injectChannelCombo = new JComboBox<>(new String[]{"Ch 1  (GPIO34 / DAC1)", "Ch 2  (GPIO35 / DAC2)"});
        injectChannelCombo.setToolTipText(
                "Select which DAC channel to drive.\nCh 2 requires dual-channel firmware (CHANNEL_COUNT 2).");

        controlRow.add(new JLabel("Channel:"));
        controlRow.add(injectChannelCombo);
        controlRow.add(Box.createHorizontalStrut(8));
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
        voltageGraph.setInjection(0, false, 0.0);
        voltageGraph.setInjection(1, false, 0.0);

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
                rawLabel        .setText("Ch1 ADC: —");
                voltageLabel    .setText("Ch1: — V");
                modeLabel       .setText("Mode: —");
                ch2RawLabel    .setVisible(false);
                ch2VoltageLabel.setVisible(false);
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

        // Normal CSV: millis,1,0,rawADC[,rawADC2]
        String[] parts = line.split(",");
        if (parts.length >= 4) {
            try {
                long   ms   = Long.parseLong(parts[0].trim());
                int    raw0 = Integer.parseInt(parts[3].trim());
                double v0   = (raw0 / (double) ADC_MAX) * V_REF;

                // Optional second ADC channel (dual-channel firmware only).
                int    raw1 = -1;
                double v1   = 0.0;
                if (parts.length >= 5) {
                    try {
                        raw1 = Integer.parseInt(parts[4].trim());
                        v1   = (raw1 / (double) ADC_MAX) * V_REF;
                    } catch (NumberFormatException ignored2) {
                        raw1 = -1;
                    }
                }

                final String timeStr = LocalTime.now().format(TIME_FMT);
                final int    fr1     = raw1;
                final double fv0     = v0;
                final double fv1     = v1;
                final long   fMs     = ms;
                final String logLine = raw1 >= 0
                        ? String.format("%s  Ch1: %5.3f V (raw %4d)  Ch2: %5.3f V (raw %4d)  t=%dms",
                                timeStr, v0, raw0, v1, raw1, ms)
                        : String.format("%s  %5.3f V  (raw %4d, t=%dms)",
                                timeStr, v0, raw0, ms);

                SwingUtilities.invokeLater(() -> {
                    if (!displayPaused) appendLog(logLine);
                    rawLabel    .setText("Ch1 ADC: " + raw0);
                    voltageLabel.setText(String.format("Ch1: %.3f V", fv0));
                    voltageGraph.addSample(0, fv0, raw0, fMs, timeStr);

                    if (fr1 >= 0) {
                        ch2RawLabel    .setText("Ch2 ADC: " + fr1);
                        ch2VoltageLabel.setText(String.format("Ch2: %.3f V", fv1));
                        ch2RawLabel    .setVisible(true);
                        ch2VoltageLabel.setVisible(true);
                        voltageGraph.addSample(1, fv1, fr1, fMs, timeStr);
                    }
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
            // New firmware format: "INJECT_START,CH1,<raw>,<v>V"
            //                   or "INJECT_START,CH2,<raw>,<v>V"
            // Legacy format:       "INJECT_START,<raw>,<v>V"
            String[] parts = statusPayload.split(",");
            int ch   = 0;   // default Ch1
            int vIdx = 2;   // index of the "<v>V" token in legacy format
            if (parts.length >= 2 && parts[1].startsWith("CH")) {
                ch   = "CH2".equals(parts[1]) ? 1 : 0;
                vIdx = 3;   // new format shifts the voltage token one column right
            }
            modeLabel.setText("Mode: INJECTING (Ch" + (ch + 1) + ")");
            modeLabel.setForeground(new Color(220, 120, 0));
            if (parts.length > vIdx) {
                try {
                    double v = Double.parseDouble(parts[vIdx].replace("V", "").trim());
                    voltageGraph.setInjection(ch, true, v);
                } catch (NumberFormatException ignored) {
                    voltageGraph.setInjection(ch, true, 0.0);
                }
            }
        } else if (statusPayload.contains("INJECT_STOPPED")) {
            // "INJECT_STOPPED,ALL" | "INJECT_STOPPED,CH1" | "INJECT_STOPPED,CH2"
            String[] parts = statusPayload.split(",");
            if (parts.length >= 2 && parts[1].startsWith("CH")) {
                int ch = "CH2".equals(parts[1]) ? 1 : 0;
                voltageGraph.setInjection(ch, false, 0.0);
            } else {
                // ALL or legacy — clear both channels
                voltageGraph.setInjection(0, false, 0.0);
                voltageGraph.setInjection(1, false, 0.0);
            }
            // Update mode label only if no channel is still injecting
            boolean anyInjecting = false;
            for (int i = 0; i < 2; i++) {
                // Check graph state as the authoritative source
                // (setInjection already updated internal state)
            }
            modeLabel.setText("Mode: NORMAL");
            modeLabel.setForeground(new Color(40, 160, 40));
        } else if (statusPayload.contains("NORMAL")) {
            modeLabel.setText("Mode: NORMAL");
            modeLabel.setForeground(new Color(40, 160, 40));
            voltageGraph.setInjection(0, false, 0.0);
            voltageGraph.setInjection(1, false, 0.0);
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
        currentInjectionV  = v;
        currentInjectionCh = injectChannelCombo.getSelectedIndex();

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

    /** Sends INJECT_V_CH1 / INJECT_V_CH2 and — in interval mode — schedules the pulse-end timer. */
    private void startInjectionCycle() {
        String cmd = currentInjectionCh == 0
                ? String.format("INJECT_V_CH1:%.3f", currentInjectionV)
                : String.format("INJECT_V_CH2:%.3f", currentInjectionV);
        sendCommand(cmd);
        voltageGraph.setInjection(currentInjectionCh, true, currentInjectionV);

        if (continuousRadio.isSelected()) return; // hold until Stop is pressed

        int onMs = (int) onSpinner.getValue();
        pendingTask = injScheduler.schedule(
                () -> SwingUtilities.invokeLater(this::onPulseEnd),
                onMs, TimeUnit.MILLISECONDS);
    }

    /** Called when the on-time expires; sends a channel-specific STOP and schedules the next pulse. */
    private void onPulseEnd() {
        sendCommand(currentInjectionCh == 0 ? "STOP_INJECT_CH1" : "STOP_INJECT_CH2");
        voltageGraph.setInjection(currentInjectionCh, false, 0.0);

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
        sendCommand(currentInjectionCh == 0 ? "STOP_INJECT_CH1" : "STOP_INJECT_CH2");
        voltageGraph.setInjection(currentInjectionCh, false, 0.0);
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
    //  VoltageGraph — custom scrolling chart, supports up to 2 channels
    // =========================================================================
    private static class VoltageGraph extends JPanel {

        private static final int    NUM_CHANNELS = 2;
        private static final int    BUFFER       = 500;
        private static final double V_REF        = 3.3;
        private static final int    ML = 44, MR = 10, MT = 12, MB = 26;

        private static final Color   BG       = new Color(18,  18,  18);
        private static final Color   GRID_COL = new Color(45,  45,  45);
        private static final Color   INJECT_C = new Color(230, 140,  30);
        private static final Color   AXIS_TXT = new Color(150, 150, 150);
        private static final Color   HOVER_C  = new Color(255, 255, 255, 160);
        private static final Color   TIP_BG   = new Color( 30,  30,  30, 220);

        /** One colour per channel. Ch1 = green (legacy), Ch2 = blue. */
        private static final Color[] TRACE_COLORS = {
            new Color( 50, 220,  80),
            new Color( 80, 180, 255),
        };

        // ── Per-channel ring buffers ──────────────────────────────────────────
        private final double[][] vBufs   = new double[NUM_CHANNELS][BUFFER];
        private final int[][]    rawBufs = new int   [NUM_CHANNELS][BUFFER];
        private final long[][]   msBufs  = new long  [NUM_CHANNELS][BUFFER];
        private final String[][] tBufs   = new String[NUM_CHANNELS][BUFFER];
        private final int[]      heads   = new int[NUM_CHANNELS];
        private final int[]      counts  = new int[NUM_CHANNELS];

        // ── Visibility (toggled by the Ch1 / Ch2 checkboxes) ─────────────────
        private final boolean[] visible = {true, false};

        // ── State ─────────────────────────────────────────────────────────────
        private final boolean[] injecting  = {false, false};
        private final double[]  injectionV = {0.0,   0.0};
        private boolean paused = false;
        private int     hoverX = -1;

        // ── Pause button (overlay) ────────────────────────────────────────────
        private final JButton pauseBtn;

        VoltageGraph() {
            setLayout(null);

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
                    hoverX = e.getX(); repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    hoverX = -1; repaint();
                }
            });
        }

        // ── Public API ────────────────────────────────────────────────────────

        void addSample(int channel, double v, int raw, long ms, String time) {
            if (paused || channel < 0 || channel >= NUM_CHANNELS) return;
            int h = heads[channel];
            vBufs  [channel][h] = v;
            rawBufs[channel][h] = raw;
            msBufs [channel][h] = ms;
            tBufs  [channel][h] = time;
            heads[channel] = (h + 1) % BUFFER;
            if (counts[channel] < BUFFER) counts[channel]++;
            repaint();
        }

        void setChannelVisible(int channel, boolean vis) {
            if (channel >= 0 && channel < NUM_CHANNELS) visible[channel] = vis;
            repaint();
        }

        void setInjection(int channel, boolean active, double v) {
            if (channel >= 0 && channel < NUM_CHANNELS) {
                injecting [channel] = active;
                injectionV[channel] = v;
            }
            repaint();
        }

        void reset() {
            for (int ch = 0; ch < NUM_CHANNELS; ch++) {
                heads[ch]     = 0;
                counts[ch]    = 0;
                injecting[ch] = false;
                injectionV[ch]= 0.0;
            }
            repaint();
        }

        // ── Painting ──────────────────────────────────────────────────────────

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w  = getWidth();
            int h  = getHeight();
            int pw = w - ML - MR;
            int ph = h - MT - MB;

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

            // ── Injection target lines — one dashed line per active channel ────
            float[] dash = {7f, 4f};
            for (int ch = 0; ch < NUM_CHANNELS; ch++) {
                if (!injecting[ch]) continue;
                int    iy    = MT + yPx(injectionV[ch], ph);
                Color  col   = TRACE_COLORS[ch];  // green for ch1, blue for ch2
                String label = String.format("inject Ch%d %.2fV", ch + 1, injectionV[ch]);
                g2.setColor(col);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10f, dash, 0f));
                g2.drawLine(ML, iy, ML + pw, iy);
                g2.setStroke(new BasicStroke(1f));
                g2.drawString(label, ML + 4, iy - 3);
            }

            // ── Voltage traces (one per visible channel) ──────────────────────
            // We also record hover info for the tooltip.
            // Primary reference for hover position = first visible channel with data.
            int    hoverRefIdx = -1;
            int    hoverRefCh  = -1;
            int    hoverPxSnap = hoverX;
            boolean anyData    = false;

            for (int ch = 0; ch < NUM_CHANNELS; ch++) {
                if (!visible[ch] || counts[ch] < 2) continue;
                anyData = true;

                int pts    = Math.min(counts[ch], pw);
                int oldest = (heads[ch] - pts + BUFFER) % BUFFER;

                g2.setColor(TRACE_COLORS[ch]);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int prevX = -1, prevY = -1;
                for (int i = 0; i < pts; i++) {
                    int    idx = (oldest + i) % BUFFER;
                    double v   = Math.max(0.0, Math.min(V_REF, vBufs[ch][idx]));
                    int    x   = ML + (int) Math.round((double) i / (pts - 1) * pw);
                    int    y   = MT + yPx(v, ph);

                    // Capture hover reference from the first visible channel.
                    if (hoverRefCh < 0 && hoverX >= ML && hoverX <= ML + pw) {
                        if (hoverRefIdx < 0 ||
                                Math.abs(x - hoverX) < Math.abs(hoverPxSnap - hoverX)) {
                            hoverRefIdx = idx;
                            hoverRefCh  = ch;
                            hoverPxSnap = x;
                        }
                    }

                    if (prevX >= 0) g2.drawLine(prevX, prevY, x, y);
                    prevX = x;
                    prevY = y;
                }
            }

            if (!anyData) {
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
            if (hoverRefIdx >= 0) {
                // Vertical crosshair
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(HOVER_C);
                g2.drawLine(hoverPxSnap, MT, hoverPxSnap, MT + ph);

                // Dots + channel label on each visible channel at hover position.
                // Both channels are sampled at the same rate so we use the same
                // relative offset within each channel's ring buffer.
                int refPts    = Math.min(counts[hoverRefCh], pw);
                int refOldest = (heads[hoverRefCh] - refPts + BUFFER) % BUFFER;
                // Compute i (sample offset) that gave hoverRefIdx.
                int hoverI = (hoverRefIdx - refOldest + BUFFER) % BUFFER;

                for (int ch = 0; ch < NUM_CHANNELS; ch++) {
                    if (!visible[ch] || counts[ch] < 2) continue;
                    int pts    = Math.min(counts[ch], pw);
                    int oldest = (heads[ch] - pts + BUFFER) % BUFFER;
                    int safeI  = Math.min(hoverI, pts - 1);
                    int idx    = (oldest + safeI) % BUFFER;
                    double v   = Math.max(0.0, Math.min(V_REF, vBufs[ch][idx]));
                    int    dotY = MT + yPx(v, ph);

                    g2.setColor(Color.WHITE);
                    g2.fillOval(hoverPxSnap - 4, dotY - 4, 8, 8);
                    g2.setColor(TRACE_COLORS[ch]);
                    g2.fillOval(hoverPxSnap - 2, dotY - 2, 5, 5);
                }

                // Tooltip — timestamp + one row per visible channel
                g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
                FontMetrics tfm = g2.getFontMetrics();

                String tsLine = tBufs[hoverRefCh][hoverRefIdx] != null
                        ? tBufs[hoverRefCh][hoverRefIdx] : "—";
                String tLine  = String.format("t = %d ms", msBufs[hoverRefCh][hoverRefIdx]);

                // Collect channel lines
                java.util.List<String[]> chLines = new java.util.ArrayList<>();
                for (int ch = 0; ch < NUM_CHANNELS; ch++) {
                    if (!visible[ch] || counts[ch] < 2) continue;
                    int pts    = Math.min(counts[ch], pw);
                    int oldest = (heads[ch] - pts + BUFFER) % BUFFER;
                    int safeI  = Math.min(hoverI, pts - 1);
                    int idx    = (oldest + safeI) % BUFFER;
                    double v   = Math.max(0.0, Math.min(V_REF, vBufs[ch][idx]));
                    chLines.add(new String[]{
                        String.format("Ch%d: %.3f V", ch + 1, v),
                        String.format("raw  %d",      rawBufs[ch][idx]),
                    });
                }

                int lh = tfm.getHeight();
                int rows = 2 + chLines.size() * 2; // ts + t + 2 rows per channel
                int th = lh * rows + 10;

                // Measure tooltip width
                int tw = tfm.stringWidth(tsLine);
                tw = Math.max(tw, tfm.stringWidth(tLine));
                for (String[] pair : chLines) {
                    tw = Math.max(tw, Math.max(tfm.stringWidth(pair[0]), tfm.stringWidth(pair[1])));
                }
                tw += 14;

                int anchorDotY = MT + yPx(
                        Math.max(0.0, Math.min(V_REF, vBufs[hoverRefCh][hoverRefIdx])), ph);
                int tx = hoverPxSnap + 10;
                int ty = anchorDotY - th / 2;
                if (tx + tw > w - MR)       tx = hoverPxSnap - tw - 10;
                if (ty < MT + 2)            ty = MT + 2;
                if (ty + th > MT + ph - 2)  ty = MT + ph - th - 2;

                g2.setColor(TIP_BG);
                g2.fillRoundRect(tx, ty, tw, th, 7, 7);
                g2.setColor(HOVER_C);
                g2.drawRoundRect(tx, ty, tw, th, 7, 7);

                int row = 1;
                g2.setColor(Color.WHITE);
                g2.drawString(tsLine, tx + 7, ty + lh * row++);
                g2.setColor(AXIS_TXT);
                g2.drawString(tLine, tx + 7, ty + lh * row++);
                for (int ci = 0; ci < chLines.size(); ci++) {
                    g2.setColor(TRACE_COLORS[
                            // find which channel index this entry corresponds to
                            chLines.size() == 1
                                ? (visible[0] ? 0 : 1)
                                : ci]);
                    g2.drawString(chLines.get(ci)[0], tx + 7, ty + lh * row++);
                    g2.setColor(AXIS_TXT);
                    g2.drawString(chLines.get(ci)[1], tx + 7, ty + lh * row++);
                }
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