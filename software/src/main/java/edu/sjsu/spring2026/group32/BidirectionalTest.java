package edu.sjsu.spring2026.group32;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.launcher.ConnectionStatusPanel;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interactive Swing tool for bidirectional ESP32 ↔ Java serial testing.
 *
 * <p>Always launched from {@link edu.sjsu.spring2026.group32.launcher.Launcher}, which owns
 * both {@link SerialConnectionManager} instances (one per device).  This window
 * never opens or closes a serial port — it only reads from and writes to ports
 * that are already managed by the Launcher.</p>
 *
 * <p>A device selector at the top lets the user switch between:</p>
 * <ul>
 *   <li>HTZ — 3-neuron (single channel, GPIO34)</li>
 *   <li>Pong — 6-neuron (dual channel, GPIO34 + GPIO35)</li>
 * </ul>
 *
 * <p>Left column: dark terminal showing live CSV lines.<br>
 * Right column: scrolling voltage graph (0–3.3 V) with optional injection
 * target line.</p>
 *
 * <p>Firmware counterpart: hardware/firmware/Test.ino</p>
 *
 * Protocol (Java → ESP32):
 * <pre>
 *   INJECT_V_CH1:&lt;volts&gt;   inject a voltage (0.00 – 3.30 V) on channel 1
 *   INJECT_V_CH2:&lt;volts&gt;   inject a voltage (0.00 – 3.30 V) on channel 2
 *   STOP_INJECT_CH1         revert channel 1 to real ADC readings
 *   STOP_INJECT_CH2         revert channel 2 to real ADC readings
 *   STATUS                  request a one-line status reply
 * </pre>
 */
public class BidirectionalTest extends JFrame {

    // ── Serial constants ──────────────────────────────────────────────────────
    private static final int    MAX_LINES = 500;
    private static final double V_REF     = 3.3;
    private static final int    ADC_MAX   = 4095;

    // ── Device managers (owned by Launcher) ───────────────────────────────────
    private final SerialConnectionManager htzManager;
    private final SerialConnectionManager pongManager;

    // ── Active connection ─────────────────────────────────────────────────────
    private SerialConnectionManager connectionManager;
    private ExecutorService         readerThread;
    private final AtomicBoolean     running = new AtomicBoolean(false);

    // ── Device selector ───────────────────────────────────────────────────────
    private JRadioButton          htzRadio;
    private JRadioButton          pongRadio;
    private ConnectionStatusPanel deviceStatusPanel;

    // ── Summary strip ─────────────────────────────────────────────────────────
    private JLabel rawLabel;
    private JLabel voltageLabel;
    private JLabel ch2RawLabel;
    private JLabel ch2VoltageLabel;
    private JLabel modeLabel;

    // ── Channel checkboxes ────────────────────────────────────────────────────
    private JCheckBox ch1Check;
    private JCheckBox ch2Check;

    // ── Log filtering ─────────────────────────────────────────────────────────
    /** Channel tag for system/status messages — always shown regardless of filter. */
    private static final int LOG_SYSTEM     = -1;
    private static final int MAX_LOG_ENTRIES = 1000;
    private record LogEntry(String text, int channel) {}
    private final List<LogEntry> logEntries = new ArrayList<>(MAX_LOG_ENTRIES + 64);
    /** Re-entry guard that prevents the "not-none" checkbox fix from re-triggering itself. */
    private boolean updatingChannelFilter = false;

    // ── Terminal ──────────────────────────────────────────────────────────────
    private JTextArea dataDisplay;
    private JButton   pauseBtn;
    private boolean   displayPaused = false;

    // ── Graph ─────────────────────────────────────────────────────────────────
    private VoltageGraph voltageGraph;

    // ── Injection panels (index 0 = Ch1, index 1 = Ch2) ──────────────────────
    // Ch2 panel is hidden until dual-channel firmware data arrives.
    private JPanel               ch2InjectionPanel;
    private final JTextField[]   voltageFields      = new JTextField[2];
    private final JSlider[]      voltageSliders     = new JSlider[2];
    private final JButton[]      injectBtns         = new JButton[2];
    private final JButton[]      stopInjectBtns     = new JButton[2];
    private final JLabel[]       sliderValueLabels  = new JLabel[2];
    private final JRadioButton[] continuousRadios   = new JRadioButton[2];
    private final JRadioButton[] intervalRadios     = new JRadioButton[2];
    private final JSpinner[]     onSpinners         = new JSpinner[2];   // pulse on-time  (ms)
    private final JSpinner[]     offSpinners        = new JSpinner[2];   // gap between pulses (ms)
    private final JSpinner[]     repeatSpinners     = new JSpinner[2];   // repeat count
    private final JCheckBox[]    infiniteChecks     = new JCheckBox[2];  // repeat indefinitely
    private final JPanel[]       intervalOptsPanels = new JPanel[2];     // shown only in interval mode

    // ── Injection scheduler ───────────────────────────────────────────────────
    private final ScheduledExecutorService injScheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "InjScheduler");
                t.setDaemon(true);
                return t;
            });
    private final ScheduledFuture<?>[] pendingTasks     = new ScheduledFuture[2];
    private final int[]    remainingRepeats  = new int[2];
    private final double[] currentInjectionV = new double[2];

    // ── Timestamp formatter ───────────────────────────────────────────────────
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // =========================================================================
    //  Constructor
    // =========================================================================

    /**
     * Constructs a BidirectionalTest window that can monitor both
     * Launcher-managed ESP32 devices.  Either manager may be {@code null} if
     * that device is not currently connected; the corresponding radio button
     * is shown as disabled in that case.
     *
     * <p>This window never opens or closes any port — the Launcher retains
     * ownership of both connections.</p>
     *
     * @param htzManager  manager for the Hit The Zone device
     *                    (3-neuron, single channel), or {@code null} if not connected
     * @param pongManager manager for the Pong device
     *                    (6-neuron, dual channel), or {@code null} if not connected
     */
    public BidirectionalTest(SerialConnectionManager htzManager,
                             SerialConnectionManager pongManager) {
        super("ESP32 ↔ Java Serial Test  [via Launcher]");
        this.htzManager  = htzManager;
        this.pongManager = pongManager;

        // DISPOSE, not EXIT — the Launcher must keep running.
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                // Stop reading but do NOT close either port — the Launcher owns them.
                stopReadLoop();
                appendLog("── Closed (ports stay open in Launcher) ──");
            }
        });

        buildUI();

        // Default selection: prefer HTZ; fall back to Pong; idle if neither connected.
        if (htzManager != null && htzManager.isConnected()) {
            htzRadio.setSelected(true);
            switchToDevice(htzManager, "HTZ — 3-neuron");
        } else if (pongManager != null && pongManager.isConnected()) {
            pongRadio.setSelected(true);
            switchToDevice(pongManager, "Pong — 6-neuron");
        } else {
            appendLog("── No devices connected. Connect a device in the Launcher and reopen. ──");
            setConnectedState(false);
        }

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

        root.add(buildDeviceSelectorPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(),         BorderLayout.CENTER);

        // Injection area: 1-column, 2-row stack.
        // Ch2 panel is hidden until dual-channel firmware data arrives.
        JPanel injWrapper = new JPanel(new GridLayout(0, 1, 0, 4));
        injWrapper.add(buildChannelInjectionPanel(0));
        ch2InjectionPanel = buildChannelInjectionPanel(1);
        ch2InjectionPanel.setVisible(false);
        injWrapper.add(ch2InjectionPanel);
        root.add(injWrapper, BorderLayout.SOUTH);
    }

    // ── Device selector ───────────────────────────────────────────────────────

    /**
     * Builds the device selector panel that replaces the old per-window
     * SerialConnectionPanel.  The Launcher owns both ports; this panel only
     * lets the user choose which one to monitor.
     */
    private JPanel buildDeviceSelectorPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        p.setBorder(new TitledBorder("Device  (connections managed by Launcher)"));

        htzRadio  = new JRadioButton("HTZ — 3-neuron (single ch)");
        pongRadio = new JRadioButton("Pong — 6-neuron (dual ch)");
        ButtonGroup grp = new ButtonGroup();
        grp.add(htzRadio);
        grp.add(pongRadio);

        // Only enable a radio button when its device is actually connected.
        htzRadio .setEnabled(htzManager  != null && htzManager .isConnected());
        pongRadio.setEnabled(pongManager != null && pongManager.isConnected());

        htzRadio.addActionListener(e -> {
            if (htzManager != null && htzManager.isConnected()) {
                switchToDevice(htzManager, "HTZ — 3-neuron");
            }
        });
        pongRadio.addActionListener(e -> {
            if (pongManager != null && pongManager.isConnected()) {
                switchToDevice(pongManager, "Pong — 6-neuron");
            }
        });

        deviceStatusPanel = new ConnectionStatusPanel();
        deviceStatusPanel.addDevice("HTZ",  htzManager);
        deviceStatusPanel.addDevice("Pong", pongManager);

        p.add(htzRadio);
        p.add(Box.createHorizontalStrut(20));
        p.add(pongRadio);
        p.add(Box.createHorizontalStrut(20));
        p.add(deviceStatusPanel);

        return p;
    }

    /**
     * Switches the active serial device.  Stops the current read loop, resets
     * the graph and Ch2 panels, then starts reading from the new manager.
     */
    private void switchToDevice(SerialConnectionManager mgr, String label) {
        stopReadLoop();
        connectionManager = mgr;

        // Clear log and graph for the new device context.
        appendLog("── Switched to: " + label + " ──");
        voltageGraph.reset();
        voltageGraph.setInjection(0, false, 0.0);
        voltageGraph.setInjection(1, false, 0.0);

        // Hide Ch2 panel until dual-channel data arrives from the new device.
        if (ch2InjectionPanel != null) ch2InjectionPanel.setVisible(false);
        if (ch2RawLabel      != null) ch2RawLabel    .setVisible(false);
        if (ch2VoltageLabel  != null) ch2VoltageLabel.setVisible(false);
        if (ch2Check         != null) ch2Check.setSelected(false);

        connectionManager.sendLine("STOP_INJECT");
        startReadLoop();
        setConnectedState(true);
        appendLog("Injection off by default.");
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
        ch1Check.setToolTipText("Show / hide Channel 1 in graph and log (GPIO34)");
        ch2Check.setToolTipText("Show / hide Channel 2 in graph and log (GPIO35, dual-channel firmware only)");

        // Each listener enforces "not-none": unchecking the last checked box is
        // rejected so graph and log always show at least one channel.
        ch1Check.addItemListener(e -> {
            if (updatingChannelFilter) return;
            if (!ch1Check.isSelected() && !ch2Check.isSelected()) {
                updatingChannelFilter = true;
                ch1Check.setSelected(true);   // veto — can't deselect both
                updatingChannelFilter = false;
                return;
            }
            voltageGraph.setChannelVisible(0, ch1Check.isSelected());
            rebuildLogDisplay();
        });
        ch2Check.addItemListener(e -> {
            if (updatingChannelFilter) return;
            if (!ch1Check.isSelected() && !ch2Check.isSelected()) {
                updatingChannelFilter = true;
                ch2Check.setSelected(true);   // veto — can't deselect both
                updatingChannelFilter = false;
                return;
            }
            voltageGraph.setChannelVisible(1, ch2Check.isSelected());
            rebuildLogDisplay();
        });

        labelsPanel.add(rawLabel);
        labelsPanel.add(voltageLabel);
        labelsPanel.add(ch2RawLabel);
        labelsPanel.add(ch2VoltageLabel);
        labelsPanel.add(modeLabel);
        labelsPanel.add(Box.createHorizontalStrut(12));
        labelsPanel.add(new JLabel("Show:"));
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

    // ── Injection panel for one channel (ch = 0 → Ch1, ch = 1 → Ch2) ─────────
    private JPanel buildChannelInjectionPanel(int ch) {
        String chLabel = "Channel " + (ch + 1);
        JPanel p = new JPanel(new BorderLayout(6, 4));
        p.setBorder(new TitledBorder("Voltage Injection – " + chLabel));

        // ── Slider row ───────────────────────────────────────────────────────
        JPanel sliderRow = new JPanel(new BorderLayout(6, 0));
        sliderRow.setBorder(new EmptyBorder(2, 4, 2, 4));

        voltageSliders[ch]    = new JSlider(0, 330, 0);
        sliderValueLabels[ch] = new JLabel("0.00 V");
        sliderValueLabels[ch].setFont(new Font("Monospaced", Font.BOLD, 13));
        sliderValueLabels[ch].setPreferredSize(new Dimension(56, 20));

        voltageSliders[ch].addChangeListener(e -> {
            double v = voltageSliders[ch].getValue() / 100.0;
            sliderValueLabels[ch].setText(String.format("%.2f V", v));
            voltageFields[ch].setText(String.format("%.2f", v));
        });

        sliderRow.add(new JLabel("0.00 V"),    BorderLayout.WEST);
        sliderRow.add(voltageSliders[ch],       BorderLayout.CENTER);
        sliderRow.add(new JLabel("3.30 V"),    BorderLayout.EAST);

        // ── Controls row (channel label + voltage field + buttons) ───────────
        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        voltageFields[ch] = new JTextField("0.00", 7);
        voltageFields[ch].setFont(new Font("Monospaced", Font.PLAIN, 13));
        voltageFields[ch].addActionListener(e -> syncSliderFromField(ch));

        injectBtns[ch]     = new JButton("▶  Inject");
        stopInjectBtns[ch] = new JButton("■  Stop");

        injectBtns[ch].setEnabled(false);
        stopInjectBtns[ch].setEnabled(false);

        injectBtns[ch].setBackground(new Color(60, 120, 200));
        injectBtns[ch].setForeground(Color.WHITE);
        injectBtns[ch].setOpaque(true);

        stopInjectBtns[ch].setBackground(new Color(190, 100, 30));
        stopInjectBtns[ch].setForeground(Color.WHITE);
        stopInjectBtns[ch].setOpaque(true);

        controlRow.add(new JLabel(chLabel + ":"));
        controlRow.add(new JLabel("Voltage (V):"));
        controlRow.add(voltageFields[ch]);
        controlRow.add(sliderValueLabels[ch]);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(injectBtns[ch]);
        controlRow.add(stopInjectBtns[ch]);

        // ── Mode row (Continuous / Interval) ─────────────────────────────────
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        continuousRadios[ch] = new JRadioButton("Continuous");
        continuousRadios[ch].setToolTipText("Inject and hold until Stop is pressed");
        continuousRadios[ch].setSelected(true);

        intervalRadios[ch] = new JRadioButton("Interval");
        intervalRadios[ch].setToolTipText("Pulse on/off repeatedly with configurable on-time and off-time");

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(continuousRadios[ch]);
        modeGroup.add(intervalRadios[ch]);

        // Interval options — shown only when interval mode is active
        onSpinners[ch]  = new JSpinner(new SpinnerNumberModel(200, 10, 60000, 50));
        offSpinners[ch] = new JSpinner(new SpinnerNumberModel(300, 10, 60000, 50));
        onSpinners[ch] .setPreferredSize(new Dimension(72, 26));
        offSpinners[ch].setPreferredSize(new Dimension(72, 26));

        repeatSpinners[ch] = new JSpinner(new SpinnerNumberModel(5, 1, 9999, 1));
        repeatSpinners[ch].setPreferredSize(new Dimension(60, 26));

        infiniteChecks[ch] = new JCheckBox("∞");
        infiniteChecks[ch].setToolTipText("Repeat indefinitely until Stop is pressed");

        // ∞ disables the repeat count spinner
        infiniteChecks[ch].addItemListener(e ->
                repeatSpinners[ch].setEnabled(!infiniteChecks[ch].isSelected()));

        intervalOptsPanels[ch] = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        intervalOptsPanels[ch].add(new JLabel("On:"));
        intervalOptsPanels[ch].add(onSpinners[ch]);
        intervalOptsPanels[ch].add(new JLabel("ms   Off:"));
        intervalOptsPanels[ch].add(offSpinners[ch]);
        intervalOptsPanels[ch].add(new JLabel("ms   Repeat:"));
        intervalOptsPanels[ch].add(repeatSpinners[ch]);
        intervalOptsPanels[ch].add(new JLabel("×"));
        intervalOptsPanels[ch].add(infiniteChecks[ch]);
        intervalOptsPanels[ch].setVisible(false);

        // Toggle interval options visibility when mode changes
        intervalRadios[ch].addItemListener(e -> {
            boolean iv = intervalRadios[ch].isSelected();
            intervalOptsPanels[ch].setVisible(iv);
            repeatSpinners[ch].setEnabled(iv && !infiniteChecks[ch].isSelected());
        });

        modeRow.add(new JLabel("Mode:"));
        modeRow.add(continuousRadios[ch]);
        modeRow.add(intervalRadios[ch]);
        modeRow.add(intervalOptsPanels[ch]);

        JPanel inner = new JPanel(new BorderLayout(0, 2));
        inner.add(sliderRow,  BorderLayout.NORTH);
        inner.add(controlRow, BorderLayout.CENTER);
        inner.add(modeRow,    BorderLayout.SOUTH);
        p.add(inner, BorderLayout.CENTER);

        injectBtns[ch].addActionListener(e -> injectVoltage(ch));
        stopInjectBtns[ch].addActionListener(e -> stopInjection(ch));

        return p;
    }

    // =========================================================================
    //  Read loop
    // =========================================================================

    /**
     * Stops the serial read loop without closing the underlying port.
     * The Launcher retains ownership of the connection.
     */
    private void stopReadLoop() {
        if (!running.getAndSet(false)) return;
        cancelPendingTask(0);
        cancelPendingTask(1);
        if (readerThread != null) {
            readerThread.shutdownNow();
            readerThread = null;
        }
    }

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
                            // Keep the SerialConnectionPanel watchdog alive.
                            // The watchdog tracks heartbeats via getNextLine(), but
                            // this read loop bypasses that path and reads raw bytes
                            // directly.  Without this call, lastRxMs stales out after
                            // 3 s and the watchdog closes the port underneath us.
                            connectionManager.refreshHeartbeat();
                            handleIncomingLine(line.trim());
                        }
                    } catch (java.io.IOException readEx) {
                        // An IOException here is usually a semi-blocking timeout
                        // (no bytes arrived within the 2 s window) rather than a
                        // physical disconnection.  Check the port first.
                        if (!running.get()) {
                            break;  // deliberate stopReadLoop() was called — exit quietly
                        }
                        if (connectionManager != null && connectionManager.isConnected()) {
                            // Port is still alive — just a quiet moment; keep looping.
                            continue;
                        }
                        // Port has actually closed — report and stop.
                        final String msg = readEx.getMessage();
                        SwingUtilities.invokeLater(() -> {
                            appendLog("Connection lost: " + msg);
                            stopReadLoop();
                            setConnectedState(false);
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
                        stopReadLoop();
                        setConnectedState(false);
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
                    }
                }

                final String timeStr    = LocalTime.now().format(TIME_FMT);
                final int    fr1        = raw1;
                final double fv0        = v0;
                final double fv1        = v1;
                final long   fMs        = ms;
                // Each channel gets its own tagged log entry so the filter can hide
                // one without affecting the other.
                final String ch1LogLine = String.format(
                        "%s  Ch1: %5.3f V (raw %4d)  t=%dms", timeStr, v0, raw0, ms);
                final String ch2LogLine = raw1 >= 0 ? String.format(
                        "%s  Ch2: %5.3f V (raw %4d)  t=%dms", timeStr, v1, raw1, ms) : null;

                SwingUtilities.invokeLater(() -> {
                    appendLog(ch1LogLine, 0);
                    rawLabel    .setText("Ch1 ADC: " + raw0);
                    voltageLabel.setText(String.format("Ch1: %.3f V", fv0));
                    voltageGraph.addSample(0, fv0, raw0, fMs, timeStr);

                    if (fr1 >= 0) {
                        appendLog(ch2LogLine, 1);
                        ch2RawLabel    .setText("Ch2 ADC: " + fr1);
                        ch2VoltageLabel.setText(String.format("Ch2: %.3f V", fv1));
                        ch2RawLabel    .setVisible(true);
                        ch2VoltageLabel.setVisible(true);
                        voltageGraph.addSample(1, fv1, fr1, fMs, timeStr);
                        // Reveal the Ch2 injection panel the first time dual-channel data arrives
                        if (ch2InjectionPanel != null && !ch2InjectionPanel.isVisible()) {
                            ch2InjectionPanel.setVisible(true);
                            injectBtns[1].setEnabled(true);
                            stopInjectBtns[1].setEnabled(true);
                            ch2Check.setSelected(true);  // auto-enable Ch2 graph trace
                            revalidate();
                            repaint();
                        }
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
    //  Connected-state UI update
    // =========================================================================
    private void setConnectedState(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            injectBtns[0]    .setEnabled(connected);
            stopInjectBtns[0].setEnabled(connected);
            // Ch2 buttons are only enabled when connected AND the panel is visible
            if (ch2InjectionPanel != null && ch2InjectionPanel.isVisible()) {
                injectBtns[1]    .setEnabled(connected);
                stopInjectBtns[1].setEnabled(connected);
            }

            if (!connected) {
                rawLabel        .setText("Ch1 ADC: —");
                voltageLabel    .setText("Ch1: — V");
                modeLabel       .setText("Mode: —");
                ch2RawLabel    .setVisible(false);
                ch2VoltageLabel.setVisible(false);
                // Hide the Ch2 injection panel until dual-channel data arrives again
                if (ch2InjectionPanel != null) ch2InjectionPanel.setVisible(false);
            }
        });
    }

    // =========================================================================
    //  Log area helpers
    // =========================================================================
    private void togglePause() {
        if (!displayPaused) {
            appendLog("── Output paused ──");
            displayPaused = true;
            pauseBtn.setText("▶ Resume");
            pauseBtn.setToolTipText("Resume terminal output");
        } else {
            displayPaused = false;
            pauseBtn.setText("⏸ Pause");
            pauseBtn.setToolTipText("Pause terminal output (graph and labels keep updating)");
            rebuildLogDisplay();
            appendLog("── Output resumed ──");
        }
    }

    /** Appends a system/status message that is always visible regardless of channel filter. */
    private void appendLog(String text) {
        addLogEntry(text, LOG_SYSTEM);
    }

    /** Appends a channel-tagged data line; hidden when that channel's filter checkbox is off. */
    private void appendLog(String text, int channel) {
        addLogEntry(text, channel);
    }

    private void addLogEntry(String text, int channel) {
        logEntries.add(new LogEntry(text, channel));
        // Keep the stored list bounded — drop the oldest entry when over capacity.
        if (logEntries.size() > MAX_LOG_ENTRIES) {
            logEntries.remove(0);
        }
        // Write to the live display only when not paused and the channel passes the filter.
        if (!displayPaused && isLogChannelVisible(channel)) {
            dataDisplay.append(text + "\n");
            trimLog();
            dataDisplay.setCaretPosition(dataDisplay.getDocument().getLength());
        }
    }

    /** Returns true if a log entry with this channel tag should appear in the display. */
    private boolean isLogChannelVisible(int channel) {
        if (channel == LOG_SYSTEM) return true;
        if (channel == 0) return ch1Check.isSelected();
        if (channel == 1) return ch2Check.isSelected();
        return true;
    }

    /**
     * Rebuilds the log display from the stored entry list using the current filter.
     * No entries are deleted — only visibility changes.
     */
    private void rebuildLogDisplay() {
        StringBuilder sb = new StringBuilder();
        for (LogEntry entry : logEntries) {
            if (isLogChannelVisible(entry.channel())) {
                sb.append(entry.text()).append('\n');
            }
        }
        dataDisplay.setText(sb.toString());
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
    private void injectVoltage(int ch) {
        syncSliderFromField(ch);
        String raw = voltageFields[ch].getText().trim();
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

        cancelPendingTask(ch);
        currentInjectionV[ch] = v;

        if (intervalRadios[ch].isSelected()) {
            remainingRepeats[ch] = infiniteChecks[ch].isSelected()
                    ? Integer.MAX_VALUE
                    : (int) repeatSpinners[ch].getValue();
            int onMs  = (int) onSpinners[ch].getValue();
            int offMs = (int) offSpinners[ch].getValue();
            appendLog(String.format("Ch%d interval injection: %.3f V  on=%dms  off=%dms  ×%s",
                    ch + 1, v, onMs, offMs,
                    infiniteChecks[ch].isSelected() ? "∞" : repeatSpinners[ch].getValue()));
        }

        startInjectionCycle(ch);
    }

    /** Sends INJECT_V_CH1 / INJECT_V_CH2 and — in interval mode — schedules the pulse-end timer. */
    private void startInjectionCycle(int ch) {
        String cmd = String.format("INJECT_V_CH%d:%.3f", ch + 1, currentInjectionV[ch]);
        sendCommand(cmd);
        voltageGraph.setInjection(ch, true, currentInjectionV[ch]);

        if (continuousRadios[ch].isSelected()) return; // hold until Stop is pressed

        int onMs = (int) onSpinners[ch].getValue();
        pendingTasks[ch] = injScheduler.schedule(
                () -> SwingUtilities.invokeLater(() -> onPulseEnd(ch)),
                onMs, TimeUnit.MILLISECONDS);
    }

    /** Called when the on-time expires; sends a channel-specific STOP and schedules the next pulse. */
    private void onPulseEnd(int ch) {
        sendCommand("STOP_INJECT_CH" + (ch + 1));
        voltageGraph.setInjection(ch, false, 0.0);

        if (remainingRepeats[ch] <= 1) {
            remainingRepeats[ch] = 0;
            return;
        }
        if (remainingRepeats[ch] != Integer.MAX_VALUE) remainingRepeats[ch]--;

        int offMs = (int) offSpinners[ch].getValue();
        pendingTasks[ch] = injScheduler.schedule(
                () -> SwingUtilities.invokeLater(() -> startInjectionCycle(ch)),
                offMs, TimeUnit.MILLISECONDS);
    }

    private void stopInjection(int ch) {
        cancelPendingTask(ch);
        remainingRepeats[ch] = 0;
        sendCommand("STOP_INJECT_CH" + (ch + 1));
        voltageGraph.setInjection(ch, false, 0.0);
    }

    private void cancelPendingTask(int ch) {
        if (pendingTasks[ch] != null && !pendingTasks[ch].isDone()) {
            pendingTasks[ch].cancel(false);
        }
        pendingTasks[ch] = null;
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
    private void syncSliderFromField(int ch) {
        try {
            double v = Double.parseDouble(voltageFields[ch].getText().trim());
            v = Math.max(0.0, Math.min(V_REF, v));
            voltageSliders[ch].setValue((int) Math.round(v * 100));
            sliderValueLabels[ch].setText(String.format("%.2f V", v));
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

                    if (hoverX >= ML && hoverX <= ML + pw) {
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
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(HOVER_C);
                g2.drawLine(hoverPxSnap, MT, hoverPxSnap, MT + ph);

                int refPts    = Math.min(counts[hoverRefCh], pw);
                int refOldest = (heads[hoverRefCh] - refPts + BUFFER) % BUFFER;
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

                g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
                FontMetrics tfm = g2.getFontMetrics();

                String tsLine = tBufs[hoverRefCh][hoverRefIdx] != null
                        ? tBufs[hoverRefCh][hoverRefIdx] : "—";
                String tLine  = String.format("t = %d ms", msBufs[hoverRefCh][hoverRefIdx]);

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
                int rows = 2 + chLines.size() * 2;
                int th = lh * rows + 10;

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
}
