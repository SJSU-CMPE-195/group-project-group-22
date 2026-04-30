package edu.sjsu.spring2026.group32.launcher;

import edu.sjsu.spring2026.group32.hardware.serial.RealSerialDevice;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.serial.SerialDevice;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Reusable Swing panel that provides a complete Serial Connection UI:
 * port dropdown, refresh/connect/disconnect buttons, ESP32 filter checkbox,
 * and a live status indicator.
 *
 * <p>Callers register a {@link ConnectionListener} to be notified when a
 * connection is established or torn down, and can retrieve the active
 * {@link SerialConnectionManager} via {@link #getConnectionManager()}.</p>
 *
 * <p>This panel is the single source of truth for connection UI — it is used
 * by {@link Launcher} to provide one shared COM instance that is then passed
 * to individual programs via constructor injection.</p>
 */
public class SerialConnectionPanel extends JPanel {

    // ── Listener interface ────────────────────────────────────────────────────
    public interface ConnectionListener {
        /** Called on the EDT when a port is successfully opened. */
        void onConnected(SerialConnectionManager manager, String portLabel);
        /** Called on the EDT when the port is closed or lost. */
        void onDisconnected();
    }

    // ── ESP32 bridge keywords ──────────────────────────────────────────────────
    private static final String[] ESP32_BRIDGE_KEYWORDS = {
        "CP210", "CH340", "CH341", "FT232", "FTDI", "ESP32", "ESP8266",
        "Silicon Laboratories", "Silicon Labs"
    };

    // ── UI components ──────────────────────────────────────────────────────────
    private final JComboBox<PortItem> portSelector;
    private final JButton             refreshBtn;
    private final JButton             connectBtn;
    private final JButton             disconnectBtn;
    private final JLabel              statusLabel;
    private final JLabel              statusDot;
    private final JCheckBox           autoFilterCheck;

    // ── State ─────────────────────────────────────────────────────────────────
    private SerialConnectionManager connectionManager;
    private ConnectionListener       listener;

    /** System port name of the currently open connection, or {@code null}. */
    private String connectedPortName = null;

    // ── Expected channel count (0 = no validation) ────────────────────────────
    private int expectedChannelCount = 0;

    // ── Excluded ports supplier ───────────────────────────────────────────────
    /** Returns port names that should be hidden from the dropdown (e.g. already
     *  claimed by another panel).  Never {@code null}; defaults to empty set. */
    private Supplier<Set<String>> excludedPortsSupplier = Collections::emptySet;

    // ── Disconnect watchdog ───────────────────────────────────────────────────
    /** Polls the firmware stream heartbeat once per second to detect USB removal. */
    private ScheduledExecutorService watchdog;

    // ── Log sink (optional) ───────────────────────────────────────────────────
    /** Optional callback for status/log messages. May be null. */
    private LogSink logSink;

    public interface LogSink {
        void log(String message);
    }

    // =========================================================================
    //  Constructor
    // =========================================================================
    public SerialConnectionPanel() {
        // Two-row BorderLayout: controls on top, status below.
        // Avoids FlowLayout wrapping that would bleed into the panel beneath.
        super(new BorderLayout(0, 0));
        setBorder(new TitledBorder("Serial Connection"));

        // Narrower dropdown (200 px) so the controls row fits at minimum window width.
        // The full name is still readable in the open dropdown list.
        portSelector  = new JComboBox<>();
        portSelector.setPreferredSize(new Dimension(200, 26));

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

        // ── Row 1: port selector + buttons ───────────────────────────────────
        JPanel controlsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        controlsRow.add(new JLabel("COM Port:"));
        controlsRow.add(portSelector);
        controlsRow.add(autoFilterCheck);
        controlsRow.add(refreshBtn);
        controlsRow.add(connectBtn);
        controlsRow.add(disconnectBtn);

        // ── Row 2: status indicator (own line — never wraps into panel below) ─
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        statusRow.add(statusDot);
        statusRow.add(statusLabel);

        add(controlsRow, BorderLayout.CENTER);
        add(statusRow,   BorderLayout.SOUTH);

        refreshBtn.addActionListener(e -> refreshPorts());
        connectBtn.addActionListener(e -> connect());
        disconnectBtn.addActionListener(e -> disconnect());

        refreshPorts();
    }

    // =========================================================================
    //  Public API
    // =========================================================================

    /** Register the listener that receives connection/disconnection callbacks. */
    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    /**
     * Returns the system port name currently held open by this panel
     * (e.g. {@code "COM4"}), or {@code null} if not connected.
     *
     * <p>Intended for sibling panels to exclude this port from their dropdowns.</p>
     */
    public String getConnectedPortName() {
        return connectedPortName;
    }

    /**
     * Provide a supplier of port names that should be hidden from the COM port
     * dropdown on every {@link #refreshPorts()} call.
     *
     * <p>Typical usage: pass a lambda that returns the sibling panel's connected
     * port so users cannot accidentally select a port already in use.</p>
     */
    public void setExcludedPortsSupplier(Supplier<Set<String>> supplier) {
        this.excludedPortsSupplier = supplier != null ? supplier : Collections::emptySet;
    }

    /**
     * Set the channel count this slot expects.  After a successful {@code INFO?}
     * handshake, {@link #connect()} will warn the user (and optionally abort) if
     * the connected device reports a different channel count.
     *
     * <p>Pass {@code 0} (the default) to disable validation.</p>
     */
    public void setExpectedChannelCount(int n) {
        this.expectedChannelCount = n;
    }

    /** Register an optional sink for log/status messages. */
    public void setLogSink(LogSink logSink) {
        this.logSink = logSink;
    }

    /**
     * Returns the active {@link SerialConnectionManager}, or {@code null}
     * if not currently connected.
     */
    public SerialConnectionManager getConnectionManager() {
        return connectionManager;
    }

    /** @return {@code true} if a port is currently open. */
    public boolean isConnected() {
        return connectionManager != null && connectionManager.isConnected();
    }

    /**
     * Programmatically disconnect.  Safe to call even if already disconnected.
     * Notifies the registered {@link ConnectionListener}.
     */
    public void disconnect() {
        if (connectionManager == null) return;
        stopWatchdog();
        connectionManager.disconnect();
        connectionManager = null;
        connectedPortName = null;
        setConnectedState(false, null);
        log("── Disconnected ──");
        if (listener != null) listener.onDisconnected();
    }

    // =========================================================================
    //  Port management
    // =========================================================================
    public void refreshPorts() {
        PortItem prev = (PortItem) portSelector.getSelectedItem();
        String prevName = (prev != null && !prev.isPlaceholder())
                ? prev.device.getSystemPortName() : null;

        portSelector.removeAllItems();
        portSelector.addItem(new PortItem(null));   // blank placeholder always first

        SerialDevice[] all = RealSerialDevice.getRealPorts();
        boolean filter = autoFilterCheck.isSelected();
        Set<String> excluded = excludedPortsSupplier.get();

        int shown = 0;
        int reselect = -1;
        for (SerialDevice d : all) {
            if (filter && !looksLikeEsp32Bridge(d)) continue;
            if (excluded.contains(d.getSystemPortName())) continue;   // already used by another panel
            portSelector.addItem(new PortItem(d));
            shown++;
            if (d.getSystemPortName().equals(prevName)) reselect = shown;
        }

        if (reselect >= 0) portSelector.setSelectedIndex(reselect);

        if (shown == 0) {
            if (!excluded.isEmpty() && all.length > 0) {
                log(String.format(
                        "No ports available (%d in use by another slot, %s). " +
                        "Disconnect the other device first.",
                        excluded.size(),
                        filter ? "ESP32 filter on" : "filter off"));
            } else if (filter && all.length > 0) {
                log(String.format(
                        "No ESP32 ports found (%d other port(s) hidden by filter). " +
                        "Uncheck 'ESP32 only' to see all.", all.length));
            } else {
                log("No serial ports found. Plug in the ESP32 and press Refresh.");
            }
        } else {
            log(String.format("Found %d port(s)%s. Select your ESP32 and press Connect.",
                    shown, filter ? " (ESP32 filter on)" : ""));
        }
    }

    private static boolean looksLikeEsp32Bridge(SerialDevice d) {
        String name = d.getDescriptivePortName().toUpperCase();
        for (String kw : ESP32_BRIDGE_KEYWORDS) {
            if (name.contains(kw.toUpperCase())) return true;
        }
        return false;
    }

    // =========================================================================
    //  Connect
    // =========================================================================
    private void connect() {
        PortItem item = (PortItem) portSelector.getSelectedItem();
        if (item == null || item.device == null) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "No port selected. Press Refresh and choose your ESP32.",
                    "No Port", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ── Unsupported device guard ──────────────────────────────────────────
        // Warn if the selected port doesn't match any known ESP32 USB-UART bridge.
        // This can happen when the ESP32 filter is unchecked or a stale port is selected.
        if (!looksLikeEsp32Bridge(item.device)) {
            String desc = item.device.getDescriptivePortName();
            String portName = item.device.getSystemPortName();
            String displayName = desc.isBlank() ? portName : portName + "  " + desc;

            int choice = JOptionPane.showOptionDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "<html><b>This port does not look like a supported ESP32 device.</b><br><br>"
                    + "<b>Port:</b> " + displayName + "<br><br>"
                    + "Supported devices use a known USB-UART bridge<br>"
                    + "(CP210x, CH340, CH341, FT232, FTDI, or ESP32 native USB).<br><br>"
                    + "Connecting to an unsupported device may produce garbage data<br>"
                    + "or conflict with another application using this port.",
                    "Unsupported Device",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new String[]{"Cancel", "Connect Anyway"},
                    "Cancel");

            if (choice != 1) {
                log("Cancelled — " + displayName + " is not a recognised ESP32 bridge.");
                return;
            }
            log("⚠ Connecting to unrecognised device: " + displayName);
        }

        SerialConnectionManager mgr =
                new SerialConnectionManager(RealSerialDevice::getRealPorts, 2000);

        if (!mgr.connectTo(item.device)) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "Could not open " + item.device.getSystemPortName() +
                    ".\nIs another application using it?",
                    "Connection Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Send INFO? and parse the #INFO: capability response from the firmware.
        boolean gotInfo = mgr.readInfoHandshake(20);

        // Short COM name used in the status label (e.g. "COM4").
        // Full descriptive label goes to the log only — keeps the status row from wrapping.
        String portShort = item.device.getSystemPortName();

        // Full label for log messages.
        String logLabel = item.toString();
        if (gotInfo) {
            logLabel += "  [" + mgr.getDeviceName() + ", CH=" + mgr.getDeviceChannelCount() + "]";
            log("Device: " + mgr.getDeviceName() + ", channels: " + mgr.getDeviceChannelCount());
        } else {
            log("Warning: no #INFO response from device — channel count unknown");
        }

        // ── Mismatch check ────────────────────────────────────────────────────
        if (gotInfo && expectedChannelCount > 0
                && mgr.getDeviceChannelCount() != expectedChannelCount) {

            int actual = mgr.getDeviceChannelCount();
            String neededDesc = expectedChannelCount == 1
                    ? "1-channel  (Hit The Zone / 3-neuron)"
                    : "2-channel  (Pong / 6-neuron)";
            String foundDesc = actual == 1
                    ? "1-channel  (Hit The Zone / 3-neuron)"
                    : actual + "-channel  (Pong / 6-neuron)";

            int choice = JOptionPane.showOptionDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "<html><b>Wrong ESP32 connected to this slot.</b><br><br>"
                    + "This slot expects a <b>" + neededDesc + "</b> device,<br>"
                    + "but the connected ESP32 reports <b>" + foundDesc + "</b>.<br><br>"
                    + "Disconnect and connect the correct device, or continue anyway?</html>",
                    "Wrong Device",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new String[]{"Disconnect", "Continue Anyway"},
                    "Disconnect");

            if (choice != 1) {
                // User chose to disconnect — abort and let them fix it.
                mgr.disconnect();
                log("⚠ Disconnected: wrong device for this slot "
                        + "(CH=" + actual + ", expected CH=" + expectedChannelCount + ")");
                return;
            }

            // User chose to continue with the mismatched device — show orange status.
            log("⚠ Proceeding with wrong device "
                    + "(CH=" + actual + ", expected CH=" + expectedChannelCount + ")");
            log("── Connected (mismatch): " + logLabel + " ──");
            connectionManager = mgr;
            connectedPortName = item.device.getSystemPortName();
            setConnectedState(true,
                    portShort + " — ⚠ CH=" + actual + " (need " + expectedChannelCount + ")",
                    new Color(220, 140, 0));
            if (listener != null) listener.onConnected(connectionManager, logLabel);
            startWatchdog();
            return;
        }

        // ── Normal connect path ───────────────────────────────────────────────
        // Compact status text keeps the FlowLayout row from wrapping.
        String shortStatus = portShort + (gotInfo ? " — CH=" + mgr.getDeviceChannelCount() : "");
        connectionManager = mgr;
        connectedPortName = item.device.getSystemPortName();
        setConnectedState(true, shortStatus, new Color(40, 190, 40));
        log("── Connected: " + logLabel + " ──");

        if (listener != null) listener.onConnected(connectionManager, logLabel);
        startWatchdog();
    }

    // =========================================================================
    //  Disconnect watchdog
    // =========================================================================

    /**
     * Starts a 1-second polling loop that detects surprise USB removal by
     * watching the firmware's data stream rather than OS-level port state.
     *
     * <p>The ESP32 firmware sends a CSV line every 10 ms. Each successful
     * {@link SerialConnectionManager#getNextLine()} call updates a heartbeat
     * timestamp. If 3 000 ms pass without a line — 300 missed frames — the
     * device is treated as gone and the disconnect flow is triggered.</p>
     *
     * <p>A secondary {@code isConnected()} check catches cases where a read
     * exception already caused the manager to self-disconnect (e.g. a Scanner
     * IOException surfaced before the heartbeat timeout).</p>
     */
    private void startWatchdog() {
        stopWatchdog();
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "serial-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleAtFixedRate(() -> {
            if (connectionManager == null) return;
            boolean streamSilent  = connectionManager.isRxTimedOut();
            boolean managerClosed = !connectionManager.isConnected();
            if (streamSilent || managerClosed) {
                SwingUtilities.invokeLater(this::handleUnexpectedDisconnect);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopWatchdog() {
        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }
    }

    /**
     * Called on the EDT when the watchdog detects the port has dropped.
     * Resets all UI controls and notifies the registered listener.
     */
    private void handleUnexpectedDisconnect() {
        if (connectionManager == null) return; // already handled
        log("⚠ Device disconnected unexpectedly.");
        try { connectionManager.disconnect(); } catch (Exception ignored) {}
        connectionManager = null;
        connectedPortName = null;
        portSelector.setSelectedIndex(0);       // reset to "-- Select a port --"
        setConnectedState(false, null);
        refreshPorts();
        if (listener != null) listener.onDisconnected();
    }

    // =========================================================================
    //  UI state helpers
    // =========================================================================

    /** Disconnect overload — always uses red dot. */
    private void setConnectedState(boolean connected, String portLabel) {
        setConnectedState(connected, portLabel, new Color(40, 190, 40));
    }

    /**
     * Update all UI controls to reflect the connected/disconnected state.
     *
     * @param connected  {@code true} to show the connected state
     * @param statusText Short text shown next to the dot (e.g. {@code "COM4 — CH=1"}).
     *                   Ignored when {@code connected} is {@code false}.
     * @param dotColor   Dot color when connected (green for OK, orange for mismatch).
     */
    private void setConnectedState(boolean connected, String statusText, Color dotColor) {
        SwingUtilities.invokeLater(() -> {
            connectBtn.setEnabled(!connected);
            disconnectBtn.setEnabled(connected);
            refreshBtn.setEnabled(!connected);
            portSelector.setEnabled(!connected);
            autoFilterCheck.setEnabled(!connected);

            if (connected) {
                statusDot.setForeground(dotColor);
                statusLabel.setText(statusText);
            } else {
                statusDot.setForeground(Color.RED);
                statusLabel.setText("Disconnected");
            }
        });
    }

    private void log(String msg) {
        if (logSink != null) logSink.log(msg);
    }

    // =========================================================================
    //  PortItem — combo box display model
    // =========================================================================
    private record PortItem(SerialDevice device) {
        boolean isPlaceholder() { return device == null; }

        @Override
        public String toString() {
            if (device == null) return "-- Select a port --";
            String sys  = device.getSystemPortName();
            String desc = device.getDescriptivePortName();
            return desc.isBlank() ? sys : sys + "  " + desc;
        }
    }
}
