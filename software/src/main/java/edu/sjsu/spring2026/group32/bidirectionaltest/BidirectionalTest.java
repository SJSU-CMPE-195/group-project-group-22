package edu.sjsu.spring2026.group32.bidirectionaltest;

import edu.sjsu.spring2026.group32.hardware.VoltageInjector;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
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
 * Interactive Swing tool for bidirectional ESP32 and Java serial testing.
 *
 * <p>Always launched from {@code Launcher}, which owns both
 * {@link SerialConnectionManager} instances. This window never opens or closes
 * a serial port. It only reads from and writes to ports that are already
 * managed by the Launcher.</p>
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

    private SerialConnectionManager connectionManager;
    private ExecutorService readerThread;

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
        liveDataPanel.setChannelVisibilityListener(voltageGraph::setChannelVisible);

        DeviceSelectorPanel selectorPanel = new DeviceSelectorPanel(
                htzManager,
                pongManager,
                () -> switchToDevice(htzManager, "HTZ - 3-neuron"),
                () -> switchToDevice(pongManager, "Pong - 6-neuron"));

        injectionPanels[0] = new InjectionChannelPanel(1, () -> injectVoltage(0), () -> stopInjection(0));
        injectionPanels[1] = new InjectionChannelPanel(2, () -> injectVoltage(1), () -> stopInjection(1));
        ch2InjectionPanel = injectionPanels[1];
        ch2InjectionPanel.setVisible(false);

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

        voltageGraph.setBorder(new TitledBorder("Voltage (V)"));
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

        voltageGraph.reset();
        voltageGraph.setInjection(0, false, 0.0);
        voltageGraph.setInjection(1, false, 0.0);
        liveDataPanel.resetReadings();
        hideSecondChannel();

        liveDataPanel.appendSystemLog("-- Switched to: " + label + " --");
        voltageInjector.stopInjection();
        startReadLoop();
        setConnectedState(true);
        liveDataPanel.appendSystemLog("Injection off by default.");
    }

    private void stopReadLoop() {
        if (!running.getAndSet(false)) {
            return;
        }

        cancelPendingTask(0);
        cancelPendingTask(1);
        if (readerThread != null) {
            readerThread.shutdownNow();
            readerThread = null;
        }
    }

    private void startReadLoop() {
        if (connectionManager == null || !connectionManager.isConnected()) {
            setConnectedState(false);
            return;
        }

        running.set(true);
        readerThread = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ESP32-Reader");
            thread.setDaemon(true);
            return thread;
        });

        readerThread.execute(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connectionManager.getInputStream()))) {

                while (running.get()) {
                    try {
                        String line = reader.readLine();
                        if (line != null && !line.isBlank()) {
                            connectionManager.refreshHeartbeat();
                            handleIncomingLine(line.trim());
                        }
                    } catch (java.io.IOException readException) {
                        if (!running.get()) {
                            break;
                        }
                        if (connectionManager != null && connectionManager.isConnected()) {
                            continue;
                        }
                        String message = readException.getMessage();
                        SwingUtilities.invokeLater(() -> {
                            liveDataPanel.appendSystemLog("Connection lost: " + message);
                            stopReadLoop();
                            setConnectedState(false);
                        });
                        break;
                    }
                }
            } catch (Exception openException) {
                if (running.get()) {
                    String message = openException.getMessage();
                    SwingUtilities.invokeLater(() -> {
                        liveDataPanel.appendSystemLog("Reader error: " + message);
                        stopReadLoop();
                        setConnectedState(false);
                    });
                }
            }
        });
    }

    private void handleIncomingLine(String line) {
        if (line.startsWith("STATUS,")) {
            String payload = line.substring(7);
            SwingUtilities.invokeLater(() -> {
                liveDataPanel.appendSystemLog("Status: " + payload);
                updateModeAndGraph(payload);
            });
            return;
        }

        String[] parts = line.split(",");
        if (parts.length >= 4) {
            try {
                long millis = Long.parseLong(parts[0].trim());
                int raw0 = Integer.parseInt(parts[3].trim());
                double volts0 = (raw0 / (double) ADC_MAX) * V_REF;

                int raw1 = -1;
                double volts1 = 0.0;
                if (parts.length >= 5) {
                    try {
                        raw1 = Integer.parseInt(parts[4].trim());
                        volts1 = (raw1 / (double) ADC_MAX) * V_REF;
                    } catch (NumberFormatException ignored) {
                    }
                }

                String time = LocalTime.now().format(TIME_FMT);
                int secondRaw = raw1;
                double firstVolts = volts0;
                double secondVolts = volts1;
                String ch1LogLine = String.format("%s  Ch1: %5.3f V (raw %4d)  t=%dms",
                        time, volts0, raw0, millis);
                String ch2LogLine = raw1 >= 0
                        ? String.format("%s  Ch2: %5.3f V (raw %4d)  t=%dms", time, volts1, raw1, millis)
                        : null;

                SwingUtilities.invokeLater(() -> {
                    liveDataPanel.appendChannelLog(ch1LogLine, 0);
                    liveDataPanel.setPrimaryReading(raw0, firstVolts);
                    voltageGraph.addSample(0, firstVolts, raw0, millis, time);

                    if (secondRaw >= 0) {
                        revealSecondChannel();
                        liveDataPanel.appendChannelLog(ch2LogLine, 1);
                        liveDataPanel.setSecondaryReading(secondRaw, secondVolts);
                        voltageGraph.addSample(1, secondVolts, secondRaw, millis, time);
                    }
                });
                return;
            } catch (NumberFormatException ignored) {
            }
        }

        SwingUtilities.invokeLater(() -> liveDataPanel.appendSystemLog("[RAW] " + line));
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
            } else {
                voltageGraph.setInjection(0, false, 0.0);
                voltageGraph.setInjection(1, false, 0.0);
            }
            liveDataPanel.setModeNormal();
            return;
        }

        if (statusPayload.contains("NORMAL")) {
            liveDataPanel.setModeNormal();
            voltageGraph.setInjection(0, false, 0.0);
            voltageGraph.setInjection(1, false, 0.0);
        }
    }

    private void setConnectedState(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            injectionPanels[0].setControlsEnabled(connected);
            injectionPanels[1].setControlsEnabled(connected && ch2InjectionPanel.isVisible());

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

    private void revealSecondChannel() {
        if (ch2InjectionPanel.isVisible()) {
            return;
        }

        liveDataPanel.setDualChannelAvailable(true);
        liveDataPanel.setChannelSelected(1, true);
        ch2InjectionPanel.setVisible(true);
        injectionPanels[1].setControlsEnabled(hasActiveConnection());
        revalidate();
        repaint();
    }

    private void hideSecondChannel() {
        liveDataPanel.setDualChannelAvailable(false);
        ch2InjectionPanel.setVisible(false);
        voltageGraph.setChannelVisible(1, false);
        voltageGraph.setInjection(1, false, 0.0);
        injectionPanels[1].setControlsEnabled(false);
    }
}
