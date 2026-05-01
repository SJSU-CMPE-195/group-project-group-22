package edu.sjsu.spring2026.group32.launcher.ui;

import edu.sjsu.spring2026.group32.hardware.serial.RealSerialDevice;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.serial.SerialDevice;
import edu.sjsu.spring2026.group32.launcher.Launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reusable Swing panel that provides a complete serial connection UI:
 * port dropdown, refresh/connect/disconnect buttons, ESP32 filter checkbox,
 * and a live status indicator.
 *
 * <p>Callers register a {@link ConnectionListener} to be notified when a
 * connection is established or torn down, and can retrieve the active
 * {@link SerialConnectionManager} via {@link #getConnectionManager()}.</p>
 *
 * <p>This panel is the single source of truth for connection UI. {@link Launcher}
 * uses it to provide one shared COM instance per slot and then passes that live
 * manager into individual programs via constructor injection.</p>
 */
public class SerialConnectionPanel extends JPanel {
    private static final int PORT_SELECTOR_WIDTH = 200;
    private static final int STATUS_LABEL_WIDTH = 170;
    private static final int PANEL_MIN_HEIGHT = 74;

    public interface ConnectionListener {
        /** Called on the EDT when a port is successfully opened. */
        void onConnected(SerialConnectionManager manager, String portLabel);

        /** Called on the EDT when the port is closed or lost. */
        void onDisconnected();
    }

    private final JComboBox<PortItem> portSelector;
    private final JButton refreshBtn;
    private final JButton connectBtn;
    private final JButton disconnectBtn;
    private final JLabel statusLabel;
    private final JLabel statusDot;
    private final JCheckBox autoFilterCheck;

    private final SerialConnectionManager connectionManager;
    private ConnectionListener listener;

    /** System port name of the currently open connection, or {@code null}. */
    private String connectedPortName = null;

    /** Expected device channel count for this slot; 0 disables validation. */
    private int expectedChannelCount = 0;

    /**
     * Returns port names that should be hidden from the dropdown
     * (for example, already claimed by another slot).
     */
    private Supplier<Set<String>> excludedPortsSupplier = Collections::emptySet;

    /**
     * Registered on every successful connect. The manager's watchdog forwards
     * disconnect events back to this panel on the EDT.
     */
    private final SerialConnectionManager.SerialListener disconnectWatcher =
            new SerialConnectionManager.SerialListener() {
                @Override
                public void onDisconnected(String reason) {
                    SwingUtilities.invokeLater(SerialConnectionPanel.this::handleUnexpectedDisconnect);
                }
            };

    /** Optional sink for status/log messages. */
    private LogSink logSink;

    public interface LogSink {
        void log(String message);
    }

    public SerialConnectionPanel() {
        super(new BorderLayout(0, 0));
        setBorder(new TitledBorder("Serial Connection"));
        setMinimumSize(new Dimension(0, PANEL_MIN_HEIGHT));

        connectionManager = new SerialConnectionManager(RealSerialDevice::getRealPorts, 2000);

        portSelector = new JComboBox<>();
        portSelector.setPreferredSize(new Dimension(PORT_SELECTOR_WIDTH, 26));
        portSelector.setMinimumSize(new Dimension(PORT_SELECTOR_WIDTH, 26));

        refreshBtn = new JButton("Refresh");
        connectBtn = new JButton("Connect");
        disconnectBtn = new JButton("Disconnect");
        disconnectBtn.setEnabled(false);

        statusDot = new JLabel("●");
        statusDot.setForeground(Color.RED);

        statusLabel = new JLabel("Disconnected");
        statusLabel.setPreferredSize(new Dimension(STATUS_LABEL_WIDTH, 18));
        statusLabel.setMinimumSize(new Dimension(STATUS_LABEL_WIDTH, 18));
        statusLabel.setMaximumSize(new Dimension(STATUS_LABEL_WIDTH, 18));

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

        JPanel controlsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        controlsRow.setOpaque(false);
        controlsRow.add(new JLabel("COM Port:"));
        controlsRow.add(portSelector);
        controlsRow.add(autoFilterCheck);
        controlsRow.add(refreshBtn);
        controlsRow.add(connectBtn);
        controlsRow.add(disconnectBtn);

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        statusRow.setOpaque(false);
        statusRow.setBorder(new EmptyBorder(0, 0, 2, 0));
        statusRow.add(statusDot);
        statusRow.add(statusLabel);

        add(controlsRow, BorderLayout.CENTER);
        add(statusRow, BorderLayout.SOUTH);

        refreshBtn.addActionListener(e -> refreshPorts());
        connectBtn.addActionListener(e -> connect());
        disconnectBtn.addActionListener(e -> disconnect());

        refreshPorts();
    }

    /** Register the listener that receives connection/disconnection callbacks. */
    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    /**
     * Returns the system port name currently held open by this panel
     * (for example, {@code "COM4"}), or {@code null} if not connected.
     */
    public String getConnectedPortName() {
        return connectedPortName;
    }

    /**
     * Provide a supplier of port names that should be hidden from the COM port
     * dropdown on every {@link #refreshPorts()} call.
     */
    public void setExcludedPortsSupplier(Supplier<Set<String>> supplier) {
        this.excludedPortsSupplier = supplier != null ? supplier : Collections::emptySet;
    }

    /**
     * Set the channel count this slot expects. After a successful {@code INFO?}
     * handshake, {@link #connect()} warns and disconnects if the connected
     * device reports a different channel count.
     */
    public void setExpectedChannelCount(int n) {
        this.expectedChannelCount = n;
    }

    /** Register an optional sink for log/status messages. */
    public void setLogSink(LogSink logSink) {
        this.logSink = logSink;
    }

    /** Returns this panel's long-lived serial connection manager. */
    public SerialConnectionManager getConnectionManager() {
        return connectionManager;
    }

    /** @return {@code true} if a port is currently open. */
    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    /**
     * Programmatically disconnect. Safe to call even if already disconnected.
     * Notifies the registered {@link ConnectionListener}.
     */
    public void disconnect() {
        connectionManager.removeListener(disconnectWatcher);
        connectionManager.disconnect();
        connectedPortName = null;
        setConnectedState();
        log("-- Disconnected --");
        if (listener != null) {
            listener.onDisconnected();
        }
    }

    /** Refresh the visible COM-port list. */
    public void refreshPorts() {
        PortItem prev = (PortItem) portSelector.getSelectedItem();
        String prevName = (prev != null && !prev.isPlaceholder())
                ? prev.device.getSystemPortName() : null;

        portSelector.removeAllItems();
        portSelector.addItem(new PortItem(null));

        SerialDevice[] all = RealSerialDevice.getRealPorts();
        boolean filter = autoFilterCheck.isSelected();
        Set<String> excluded = excludedPortsSupplier.get();

        int shown = 0;
        int reselect = -1;
        for (SerialDevice device : all) {
            if (filter && !SerialConnectionManager.isKnownEsp32Bridge(device)) {
                continue;
            }
            if (excluded.contains(device.getSystemPortName())) {
                continue;
            }

            portSelector.addItem(new PortItem(device));
            shown++;
            if (device.getSystemPortName().equals(prevName)) {
                reselect = shown;
            }
        }

        if (reselect >= 0) {
            portSelector.setSelectedIndex(reselect);
        }

        if (shown == 0) {
            if (!excluded.isEmpty() && all.length > 0) {
                log(String.format(
                        "No ports available (%d in use by another slot, %s). Disconnect the other device first.",
                        excluded.size(),
                        filter ? "ESP32 filter on" : "filter off"));
            } else if (filter && all.length > 0) {
                log(String.format(
                        "No ESP32 ports found (%d other port(s) hidden by filter). Uncheck 'ESP32 only' to see all.",
                        all.length));
            } else {
                log("No serial ports found. Plug in the ESP32 and press Refresh.");
            }
        } else {
            log(String.format(
                    "Found %d port(s)%s. Select your ESP32 and press Connect.",
                    shown,
                    filter ? " (ESP32 filter on)" : ""));
        }
    }

    private void connect() {
        PortItem item = (PortItem) portSelector.getSelectedItem();
        if (item == null || item.device == null) {
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "No port selected. Press Refresh and choose your ESP32.",
                    "No Port",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!SerialConnectionManager.isKnownEsp32Bridge(item.device)) {
            String desc = item.device.getDescriptivePortName();
            String portName = item.device.getSystemPortName();
            String displayName = desc.isBlank() ? portName : portName + "  " + desc;

            JOptionPane.showOptionDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "<html><b>This port does not look like a supported ESP32 device.</b><br><br>"
                            + "<b>Port:</b> " + displayName + "<br><br>"
                            + "Supported devices use a known USB-UART bridge<br>"
                            + "(CP210x, CH340, CH341, FT232, FTDI, or ESP32 native USB).<br><br>"
                            + "Connecting to an unsupported device may produce garbage data<br>"
                            + "or conflict with another application using this port.",
                    "Unsupported Device",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new String[]{"Disconnect"},
                    "Disconnect");

            log("Cancelled - " + displayName + " is not a recognized ESP32 bridge.");
            return;
        }

        if (!connectionManager.connectTo(item.device)) {
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Could not open " + item.device.getSystemPortName()
                            + ".\nIs another application using it?",
                    "Connection Failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        connectionManager.removeListener(disconnectWatcher);
        connectionManager.addListener(disconnectWatcher);

        boolean gotInfo = connectionManager.readInfoHandshake(20);
        String portShort = item.device.getSystemPortName();

        String logLabel = item.toString();
        if (gotInfo) {
            logLabel += "  [" + connectionManager.getDeviceName() + ", Channel Count: "
                    + connectionManager.getDeviceChannelCount() + "]";
            log("Device: " + connectionManager.getDeviceName() + ", channels: "
                    + connectionManager.getDeviceChannelCount());
        } else {
            log("Warning: no #INFO response from device - channel count unknown");
        }

        if (gotInfo && expectedChannelCount > 0
                && connectionManager.getDeviceChannelCount() != expectedChannelCount) {

            int actual = connectionManager.getDeviceChannelCount();
            String neededDesc = expectedChannelCount == 1
                    ? "1-channel  (Hit The Zone / 3-neuron)"
                    : "2-channel  (Pong / 6-neuron)";
            String foundDesc = actual == 1
                    ? "1-channel  (Hit The Zone / 3-neuron)"
                    : actual + "-channel  (Pong / 6-neuron)";

            JOptionPane.showOptionDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "<html><b>Wrong ESP32 connected to this slot.</b><br><br>"
                            + "This slot expects a <b>" + neededDesc + "</b> device,<br>"
                            + "but the connected ESP32 reports <b>" + foundDesc + "</b>.</html>",
                    "Wrong Device",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new String[]{"Disconnect"},
                    "Disconnect");

            connectionManager.removeListener(disconnectWatcher);
            connectionManager.disconnect();
            log("Disconnected: wrong device for this slot (Channel Count: " + actual
                    + ", expected Channel Count: " + expectedChannelCount + ")");
            return;
        }

        String shortStatus = portShort
                + (gotInfo ? " - CH" + connectionManager.getDeviceChannelCount() : "");
        connectedPortName = item.device.getSystemPortName();
        setConnectedState(true, shortStatus, new Color(40, 190, 40));
        log("-- Connected: " + logLabel + " --");

        if (listener != null) {
            listener.onConnected(connectionManager, logLabel);
        }
    }

    /**
     * Called on the EDT when the manager's internal watchdog reports a lost
     * connection. The manager has already torn down the port by then.
     */
    private void handleUnexpectedDisconnect() {
        if (connectedPortName == null) {
            return;
        }

        log("Device disconnected unexpectedly.");
        connectedPortName = null;
        portSelector.setSelectedIndex(0);
        setConnectedState();
        refreshPorts();
        if (listener != null) {
            listener.onDisconnected();
        }
    }

    /** Convenience overload for the disconnected state. */
    private void setConnectedState() {
        setConnectedState(false, null, new Color(40, 190, 40));
    }

    /**
     * Update all controls to reflect the connected/disconnected state.
     *
     * @param connected {@code true} to show the connected state
     * @param statusText short status text, such as {@code "COM4 - CH1"}
     * @param dotColor indicator color when connected
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

    private void log(String message) {
        if (logSink != null) {
            logSink.log(message);
        }
    }

    private record PortItem(SerialDevice device) {
        boolean isPlaceholder() {
            return device == null;
        }

        @Override
        public String toString() {
            if (device == null) {
                return "-- Select a port --";
            }
            String systemName = device.getSystemPortName();
            String description = device.getDescriptivePortName();
            return description.isBlank() ? systemName : systemName + "  " + description;
        }
    }
}
