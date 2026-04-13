package edu.sjsu.spring2026.group32.launcher;

import edu.sjsu.spring2026.group32.hardware.serial.RealSerialDevice;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.serial.SerialDevice;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

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
        super(new FlowLayout(FlowLayout.LEFT, 8, 4));
        setBorder(new TitledBorder("Serial Connection"));

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

        add(new JLabel("COM Port:"));
        add(portSelector);
        add(autoFilterCheck);
        add(refreshBtn);
        add(connectBtn);
        add(disconnectBtn);
        add(Box.createHorizontalStrut(12));
        add(statusDot);
        add(statusLabel);

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
        connectionManager.disconnect();
        connectionManager = null;
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

        int shown = 0;
        int reselect = -1;
        for (SerialDevice d : all) {
            if (filter && !looksLikeEsp32Bridge(d)) continue;
            portSelector.addItem(new PortItem(d));
            shown++;
            if (d.getSystemPortName().equals(prevName)) reselect = shown;
        }

        if (reselect >= 0) portSelector.setSelectedIndex(reselect);

        if (shown == 0) {
            if (filter && all.length > 0) {
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

        SerialConnectionManager mgr =
                new SerialConnectionManager(RealSerialDevice::getRealPorts, 2000);

        if (!mgr.connectTo(item.device)) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "Could not open " + item.device.getSystemPortName() +
                    ".\nIs another application using it?",
                    "Connection Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        connectionManager = mgr;

        // Send INFO? and parse the #INFO: capability response from the firmware.
        boolean gotInfo = mgr.readInfoHandshake(20);
        String label = item.toString();
        if (gotInfo) {
            label += "  [" + mgr.getDeviceName() + ", CH=" + mgr.getDeviceChannelCount() + "]";
            log("Device: " + mgr.getDeviceName() + ", channels: " + mgr.getDeviceChannelCount());
        } else {
            log("Warning: no #INFO response from device — channel count unknown");
        }

        setConnectedState(true, label);
        log("── Connected: " + label + " ──");

        if (listener != null) listener.onConnected(connectionManager, label);
    }

    // =========================================================================
    //  UI state helper
    // =========================================================================
    private void setConnectedState(boolean connected, String portLabel) {
        SwingUtilities.invokeLater(() -> {
            connectBtn.setEnabled(!connected);
            disconnectBtn.setEnabled(connected);
            refreshBtn.setEnabled(!connected);
            portSelector.setEnabled(!connected);
            autoFilterCheck.setEnabled(!connected);

            if (connected) {
                statusDot.setForeground(new Color(40, 190, 40));
                statusLabel.setText("Connected: " + portLabel);
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
