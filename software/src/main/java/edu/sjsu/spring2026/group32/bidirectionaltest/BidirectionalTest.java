package edu.sjsu.spring2026.group32.bidirectionaltest;

import edu.sjsu.spring2026.group32.hardware.VoltageInjector;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interactive Swing tool for bidirectional ESP32 and Java serial testing.
 *
 * <p>Always launched from {@code Launcher}, which owns both
 * {@link SerialConnectionManager} instances. This window never opens or closes
 * a serial port. It subscribes to Launcher-managed serial events instead of
 * reading the COM port directly.</p>
 */
public class BidirectionalTest extends JFrame {
    private static final double V_REF = 3.3;
    private static final int ADC_MAX = 4095;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final VoltageInjector voltageInjector;
    private final LiveDataPanel liveDataPanel;
    private final VoltageGraphPanel voltageGraph;
    private final InjectionChannelPanel[] injectionPanels = new InjectionChannelPanel[2];
    private final JPanel ch2InjectionPanel;
    private final ScheduledExecutorService injectionScheduler =
            Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "BidirectionalTest-Injection");
                thread.setDaemon(true);
                return thread;
            });
    private final ScheduledFuture<?>[] pendingTasks = new ScheduledFuture[2];
    private final int[] remainingRepeats = new int[2];
    private final double[] currentInjectionVoltage = new double[2];
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean dualChannelActive = false;

    private final SerialConnectionManager.SerialListener deviceListener =
            new SerialConnectionManager.SerialListener() {
                @Override
                public void onSample(SerialConnectionManager.SampleFrame frame) {
                    if (running.get()) {
                        handleSample(frame);
                    }
                }

                @Override
                public void onStatusPayload(String payload) {
                    if (running.get()) {
                        SwingUtilities.invokeLater(() -> {
                            liveDataPanel.appendSystemLog("Status: " + payload);
                            updateModeAndGraph(payload);
                        });
                    }
                }

                @Override
                public void onInfoUpdated(String deviceName, int channelCount) {
                    activeDeviceChannelCount = channelCount;
                }

                @Override
                public void onDisconnected(String reason) {
                    if (running.get()) {
                        SwingUtilities.invokeLater(() -> {
                            liveDataPanel.appendSystemLog("Connection lost: " + reason);
                            stopReadLoop();
                            setConnectedState(false);
                        });
                    }
                }
            };

    private SerialConnectionManager connectionManager;
    private int activeDeviceChannelCount = 0;

    public BidirectionalTest(SerialConnectionManager htzManager,
                             SerialConnectionManager pongManager) {
        super("ESP32 <-> Java Serial Test [via Launcher]");
        this.voltageInjector = new ActiveDeviceVoltageInjector(() -> connectionManager);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopReadLoop();
                injectionScheduler.shutdownNow();
                liveDataPanel.appendSystemLog("-- Closed (ports stay open in Launcher) --");
            }
        });

        voltageGraph = new VoltageGraphPanel();
        liveDataPanel = new LiveDataPanel();

        DeviceSelectorPanel selectorPanel = new DeviceSelectorPanel(
                htzManager,
                pongManager,
                () -> switchToDevice(htzManager, "HTZ - 3-neuron"),
                () -> switchToDevice(pongManager, "Pong - 6-neuron"));

        injectionPanels[0] = new InjectionChannelPanel(1, () -> injectVoltage(0), () -> stopInjection(0));
        injectionPanels[1] = new InjectionChannelPanel(2, () -> injectVoltage(1), () -> stopInjection(1));
        ch2InjectionPanel = injectionPanels[1];

        buildUi(selectorPanel);

        if (htzManager != null && htzManager.isConnected()) {
            selectorPanel.selectHtz();
            switchToDevice(htzManager, "HTZ - 3-neuron");
        } else if (pongManager != null && pongManager.isConnected()) {
            selectorPanel.selectPong();
            switchToDevice(pongManager, "Pong - 6-neuron");
        } else {
            liveDataPanel.appendSystemLog("-- No devices connected. Connect a device in Launcher and reopen. --");
            setConnectedState(false);
        }

        setMinimumSize(new Dimension(1000, 600));
        pack();
        setLocationRelativeTo(null);
    }

    private void buildUi(DeviceSelectorPanel selectorPanel) {
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(root);

        root.add(selectorPanel, BorderLayout.NORTH);

        voltageGraph.setPreferredSize(new Dimension(420, 420));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, liveDataPanel, voltageGraph);
        splitPane.setResizeWeight(0.52);
        splitPane.setDividerLocation(500);
        splitPane.setDividerSize(5);

        root.add(splitPane, BorderLayout.CENTER);

        JPanel injectionWrapper = new JPanel(new GridLayout(0, 1, 0, 4));
        injectionWrapper.add(injectionPanels[0]);
        injectionWrapper.add(ch2InjectionPanel);
        root.add(injectionWrapper, BorderLayout.SOUTH);
    }

    private void switchToDevice(SerialConnectionManager manager, String label) {
        stopReadLoop();
        connectionManager = manager;
        activeDeviceChannelCount = manager != null ? manager.getDeviceChannelCount() : 0;

        voltageGraph.reset();
        voltageGraph.setInjection(0, false, 0.0);
        voltageGraph.setInjection(1, false, 0.0);
        liveDataPanel.resetReadings();
        hideSecondChannel();

        liveDataPanel.appendSystemLog("-- Switched to: " + label + " --");
        if (activeDeviceChannelCount == 1) {
            liveDataPanel.appendSystemLog("Single-channel device detected; Ch2 controls disabled.");
        }
        voltageInjector.stopInjection();
        startReadLoop();
        setConnectedState(true);
        liveDataPanel.appendSystemLog("Injection off by default.");
    }

    private void stopReadLoop() {
        stopAllInjectionForCurrentDevice();
        if (!running.getAndSet(false)) {
            return;
        }

        cancelPendingTask(0);
        cancelPendingTask(1);
        if (connectionManager != null) {
            connectionManager.removeListener(deviceListener);
        }
    }

    private void stopAllInjectionForCurrentDevice() {
        if (connectionManager == null || !connectionManager.isConnected()) {
            return;
        }

        cancelPendingTask(0);
        cancelPendingTask(1);
        remainingRepeats[0] = 0;
        remainingRepeats[1] = 0;
        connectionManager.stopAllInjection();
        voltageGraph.setInjection(0, false, 0.0);
        voltageGraph.setInjection(1, false, 0.0);
        injectionPanels[0].setInjecting(false);
        injectionPanels[1].setInjecting(false);
        liveDataPanel.appendSystemLog("-> STOP_INJECT");
    }

    private void startReadLoop() {
        if (connectionManager == null || !connectionManager.isConnected()) {
            setConnectedState(false);
            return;
        }

        running.set(true);
        connectionManager.addListener(deviceListener);
    }

    private void handleSample(SerialConnectionManager.SampleFrame frame) {
        long millis = frame.millis();
        int raw0 = frame.primaryRaw();
        double firstVolts = (raw0 / (double) ADC_MAX) * V_REF;

        Integer raw1Value = frame.secondaryRaw();
        int secondRaw = raw1Value != null ? raw1Value : -1;
        double secondVolts = raw1Value != null ? (secondRaw / (double) ADC_MAX) * V_REF : 0.0;

        String time = LocalTime.now().format(TIME_FMT);
        String ch1LogLine = String.format("%s  Ch1: %5.3f V (raw %4d)  t=%dms",
                time, firstVolts, raw0, millis);
        String ch2LogLine = secondRaw >= 0
                ? String.format("%s  Ch2: %5.3f V (raw %4d)  t=%dms", time, secondVolts, secondRaw, millis)
                : null;

        SwingUtilities.invokeLater(() -> {
            liveDataPanel.appendChannelLog(ch1LogLine, 0);
            liveDataPanel.setPrimaryReading(raw0, firstVolts);
            voltageGraph.addSample(0, firstVolts, raw0, millis, time);

            if (shouldDisplaySecondChannel(secondRaw)) {
                revealSecondChannel();
                liveDataPanel.appendChannelLog(ch2LogLine, 1);
                liveDataPanel.setSecondaryReading(secondRaw, secondVolts);
                voltageGraph.addSample(1, secondVolts, secondRaw, millis, time);
            }
        });
    }

    private void updateModeAndGraph(String statusPayload) {
        if (statusPayload.contains("INJECT_START")) {
            String[] parts = statusPayload.split(",");
            int channel = 0;
            int voltageIndex = 2;
            if (parts.length >= 2 && parts[1].startsWith("CH")) {
                channel = "CH2".equals(parts[1]) ? 1 : 0;
                voltageIndex = 3;
            }

            // Only update the live-data mode indicator and graph — do NOT call
            // setInjecting(true) here. Panel state is driven exclusively by
            // startInjectionCycle / onPulseEnd / stopInjection so that injection
            // initiated by an external source (e.g. Pong) does not gray out the
            // voltage injector controls.
            liveDataPanel.setModeInjecting(channel);
            if (parts.length > voltageIndex) {
                try {
                    double volts = Double.parseDouble(parts[voltageIndex].replace("V", "").trim());
                    voltageGraph.setInjection(channel, true, volts);
                } catch (NumberFormatException ignored) {
                    voltageGraph.setInjection(channel, true, 0.0);
                }
            }
            return;
        }

        if (statusPayload.contains("INJECT_STOPPED")) {
            String[] parts = statusPayload.split(",");
            if (parts.length >= 2 && parts[1].startsWith("CH")) {
                int channel = "CH2".equals(parts[1]) ? 1 : 0;
                voltageGraph.setInjection(channel, false, 0.0);
                // Panel state is managed by onPulseEnd / stopInjection; skip here
                // to prevent external stops from interfering with our panel.
            } else {
                voltageGraph.setInjection(0, false, 0.0);
                voltageGraph.setInjection(1, false, 0.0);
            }
            if (remainingRepeats[0] == 0 && remainingRepeats[1] == 0) {
                liveDataPanel.setModeNormal();
            }
            return;
        }

        if (statusPayload.contains("NORMAL")) {
            liveDataPanel.setModeNormal();
            voltageGraph.setInjection(0, false, 0.0);
            voltageGraph.setInjection(1, false, 0.0);
            injectionPanels[0].setInjecting(false);
            injectionPanels[1].setInjecting(false);
        }
    }

    private void setConnectedState(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            injectionPanels[0].setControlsEnabled(connected);
            injectionPanels[1].setControlsEnabled(connected && dualChannelActive);
            liveDataPanel.setConnected(connected);
            voltageGraph.setConnected(connected);

            if (!connected) {
                liveDataPanel.resetReadings();
                hideSecondChannel();
            }
        });
    }

    private void injectVoltage(int channelIndex) {
        if (!hasActiveConnection()) {
            liveDataPanel.appendSystemLog("Not connected - command not sent.");
            return;
        }

        Double volts = injectionPanels[channelIndex].readVoltage(this, V_REF);
        if (volts == null) {
            return;
        }

        cancelPendingTask(channelIndex);
        currentInjectionVoltage[channelIndex] = volts;

        if (injectionPanels[channelIndex].isIntervalMode()) {
            remainingRepeats[channelIndex] = injectionPanels[channelIndex].isInfinite()
                    ? Integer.MAX_VALUE
                    : injectionPanels[channelIndex].getRepeatCount();
            liveDataPanel.appendSystemLog(String.format(
                    "Ch%d interval injection: %.3f V  on=%dms  off=%dms  x%s",
                    channelIndex + 1,
                    volts,
                    injectionPanels[channelIndex].getOnMs(),
                    injectionPanels[channelIndex].getOffMs(),
                    injectionPanels[channelIndex].isInfinite()
                            ? "inf"
                            : injectionPanels[channelIndex].getRepeatCount()));
        }

        startInjectionCycle(channelIndex);
    }

    private void startInjectionCycle(int channelIndex) {
        if (!hasActiveConnection()) {
            liveDataPanel.appendSystemLog("Not connected - command not sent.");
            return;
        }

        voltageInjector.injectVoltage(channelIndex + 1, currentInjectionVoltage[channelIndex]);
        liveDataPanel.appendSystemLog(String.format("-> INJECT_V_CH%d:%.3f",
                channelIndex + 1, currentInjectionVoltage[channelIndex]));
        voltageGraph.setInjection(channelIndex, true, currentInjectionVoltage[channelIndex]);
        injectionPanels[channelIndex].setInjecting(true);

        if (injectionPanels[channelIndex].isContinuousMode()) {
            return;
        }

        pendingTasks[channelIndex] = injectionScheduler.schedule(
                () -> SwingUtilities.invokeLater(() -> onPulseEnd(channelIndex)),
                injectionPanels[channelIndex].getOnMs(),
                TimeUnit.MILLISECONDS);
    }

    private void onPulseEnd(int channelIndex) {
        voltageInjector.stopInjection(channelIndex + 1);
        liveDataPanel.appendSystemLog("-> STOP_INJECT_CH" + (channelIndex + 1));
        voltageGraph.setInjection(channelIndex, false, 0.0);

        if (remainingRepeats[channelIndex] <= 1) {
            remainingRepeats[channelIndex] = 0;
            injectionPanels[channelIndex].setInjecting(false);
            return;
        }
        if (remainingRepeats[channelIndex] != Integer.MAX_VALUE) {
            remainingRepeats[channelIndex]--;
        }

        pendingTasks[channelIndex] = injectionScheduler.schedule(
                () -> SwingUtilities.invokeLater(() -> startInjectionCycle(channelIndex)),
                injectionPanels[channelIndex].getOffMs(),
                TimeUnit.MILLISECONDS);
    }

    private void stopInjection(int channelIndex) {
        cancelPendingTask(channelIndex);
        remainingRepeats[channelIndex] = 0;

        if (!hasActiveConnection()) {
            liveDataPanel.appendSystemLog("Not connected - command not sent.");
            return;
        }

        voltageInjector.stopInjection(channelIndex + 1);
        liveDataPanel.appendSystemLog("-> STOP_INJECT_CH" + (channelIndex + 1));
        voltageGraph.setInjection(channelIndex, false, 0.0);
        injectionPanels[channelIndex].setInjecting(false);
    }

    private void cancelPendingTask(int channelIndex) {
        if (pendingTasks[channelIndex] != null && !pendingTasks[channelIndex].isDone()) {
            pendingTasks[channelIndex].cancel(false);
        }
        pendingTasks[channelIndex] = null;
    }

    private boolean hasActiveConnection() {
        return connectionManager != null && connectionManager.isConnected() && running.get();
    }

    private boolean shouldDisplaySecondChannel(int secondRaw) {
        if (secondRaw < 0) {
            return false;
        }
        return activeDeviceChannelCount != 1;
    }

    private void revealSecondChannel() {
        if (dualChannelActive) {
            return;
        }

        dualChannelActive = true;
        liveDataPanel.setDualChannelAvailable(true);
        liveDataPanel.setChannelSelected(1, true);
        voltageGraph.setDualChannelAvailable(true);
        injectionPanels[1].setControlsEnabled(hasActiveConnection());
    }

    private void hideSecondChannel() {
        dualChannelActive = false;
        liveDataPanel.setDualChannelAvailable(false);
        voltageGraph.setDualChannelAvailable(false);
        voltageGraph.setInjection(1, false, 0.0);
        injectionPanels[1].setControlsEnabled(false);
    }
}
