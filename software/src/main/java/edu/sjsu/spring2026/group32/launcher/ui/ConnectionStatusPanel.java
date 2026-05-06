package edu.sjsu.spring2026.group32.launcher.ui;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact panel that displays a live <em>● Name: Connected</em> /
 * <em>● Name: Not connected</em> indicator for one or more
 * {@link SerialConnectionManager} instances, plus a <em>← Launcher</em>
 * button that closes the containing program window and returns focus to
 * the Launcher.
 *
 * <p><strong>Usage</strong></p>
 * <pre>
 *   ConnectionStatusPanel status = new ConnectionStatusPanel();
 *   status.addDevice("HTZ",  htzManager);
 *   status.addDevice("Pong", pongManager);
 *   someContainer.add(status);
 * </pre>
 *
 * <p>Once the panel is part of a visible Swing hierarchy, it polls its managers
 * once per second and updates the indicator colours automatically.  The internal
 * Swing timer is started on {@link #addNotify()} and stopped on
 * {@link #removeNotify()}, so no manual start/stop calls are required.</p>
 *
 * <p>The <em>← Launcher</em> button calls {@link Window#dispose()} on the
 * nearest ancestor {@link Window}, which triggers the frame's existing
 * {@code DISPOSE_ON_CLOSE} behaviour.  No additional wiring is needed in
 * each program.</p>
 *
 * <p>Passing {@code null} as a manager is safe — that entry always shows
 * "Not connected".</p>
 */
public class ConnectionStatusPanel extends JPanel {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color COLOR_CONNECTED    = new Color(40, 190, 40);
    private static final Color COLOR_DISCONNECTED = Color.RED;

    // ── Internal entry model ──────────────────────────────────────────────────
    private record Entry(String name, SerialConnectionManager manager, JLabel indicator) {}
    private final List<Entry> entries = new ArrayList<>();

    // ── Sub-panels ────────────────────────────────────────────────────────────
    /** Left/centre area — holds all indicator labels. */
    private final JPanel indicatorsPanel;

    // ── Auto-refresh timer ────────────────────────────────────────────────────
    private final Timer refreshTimer;

    // =========================================================================
    //  Constructor
    // =========================================================================

    public ConnectionStatusPanel() {
        super(new BorderLayout());
        setBorder(new TitledBorder("Hardware Status"));

        // Swing timer fires on the EDT — safe to update labels directly.
        refreshTimer = new Timer(1000, e -> refresh());

        // ── Indicators sub-panel (left / centre) ──────────────────────────────
        indicatorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        indicatorsPanel.setOpaque(false);
        add(indicatorsPanel, BorderLayout.CENTER);

        // ── Close button (right) ──────────────────────────────────────────────
        JButton closeBtn = new JButton("← Launcher");
        closeBtn.setFocusable(false);
        closeBtn.setToolTipText("Close this program and return to the Launcher");
        closeBtn.addActionListener(e -> {
            Window w = SwingUtilities.getWindowAncestor(ConnectionStatusPanel.this);
            if (w != null) w.dispose();
        });

        JPanel eastWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        eastWrap.setOpaque(false);
        eastWrap.add(closeBtn);
        add(eastWrap, BorderLayout.EAST);
    }

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Registers a device and appends its indicator label to this panel.
     * Call before adding the panel to a container.
     *
     * @param name    short display name shown in the indicator, e.g. {@code "HTZ"}
     * @param manager the connection to monitor, or {@code null} for always-disconnected
     */
    public void addDevice(String name, SerialConnectionManager manager) {
        boolean connected = isAlive(manager);
        JLabel  indicator = buildLabel(name, connected);
        entries.add(new Entry(name, manager, indicator));
        indicatorsPanel.add(indicator);
    }

    /**
     * Refreshes all indicators to reflect the current connection state.
     * Called automatically every second; may also be invoked manually.
     */
    public void refresh() {
        for (Entry e : entries) {
            boolean connected = isAlive(e.manager());
            e.indicator().setText(labelText(e.name(), connected));
            e.indicator().setForeground(connected ? COLOR_CONNECTED : COLOR_DISCONNECTED);
        }
        revalidate();
        repaint();
    }

    // =========================================================================
    //  Swing lifecycle — auto-start / auto-stop the refresh timer
    // =========================================================================

    /** Starts the refresh timer when this panel is added to a displayable hierarchy. */
    @Override
    public void addNotify() {
        super.addNotify();
        refreshTimer.start();
    }

    /** Stops the refresh timer when this panel is removed from its hierarchy. */
    @Override
    public void removeNotify() {
        refreshTimer.stop();
        super.removeNotify();
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    static boolean isAlive(SerialConnectionManager mgr) {
        return mgr != null && mgr.isConnected();
    }

    static String labelText(String name, boolean connected) {
        return "● " + name + ": " + (connected ? "Connected" : "Not connected");
    }

    private static JLabel buildLabel(String name, boolean connected) {
        JLabel lbl = new JLabel(labelText(name, connected));
        lbl.setForeground(connected ? COLOR_CONNECTED : COLOR_DISCONNECTED);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return lbl;
    }
}
