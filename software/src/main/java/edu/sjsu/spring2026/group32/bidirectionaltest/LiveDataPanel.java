package edu.sjsu.spring2026.group32.bidirectionaltest;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

final class LiveDataPanel extends JPanel {
    static final int LOG_SYSTEM = -1;
    private static final int MAX_LINES = 500;
    private static final int MAX_LOG_ENTRIES = 1000;

    private record LogEntry(String text, int channel) {}

    private final JLabel rawLabel;
    private final JLabel voltageLabel;
    private final JLabel ch2RawLabel;
    private final JLabel ch2VoltageLabel;
    private final JLabel ch1ModeLabel;
    private final JLabel ch2ModeLabel;
    private final JCheckBox ch1Check;
    private final JCheckBox ch2Check;
    private final JTextArea dataDisplay;
    private final JButton pauseButton;
    private final List<LogEntry> logEntries = new ArrayList<>(MAX_LOG_ENTRIES + 64);

    private BiConsumer<Integer, Boolean> visibilityListener = (channel, visible) -> {};
    private boolean updatingChannelFilter;
    private boolean displayPaused;

    LiveDataPanel() {
        super(new BorderLayout(4, 4));

        JPanel strip = new JPanel(new BorderLayout());
        strip.setBorder(new TitledBorder("Live Readings"));

        // --- labels ---
        rawLabel = makeSummaryLabel("Ch1 ADC: -");
        voltageLabel = makeSummaryLabel("Ch1: - V");
        ch1ModeLabel = makeSummaryLabel("Mode: -");
        ch2RawLabel = makeSummaryLabel("Ch2 ADC: -");
        ch2VoltageLabel = makeSummaryLabel("Ch2: - V");
        ch2ModeLabel = makeSummaryLabel("Mode: -");

        // --- checkboxes ---
        ch1Check = new JCheckBox("Ch 1", true);
        ch2Check = new JCheckBox("Ch 2", false);

        ch1Check.setFont(new Font("SansSerif", Font.PLAIN, 11));
        ch2Check.setFont(new Font("SansSerif", Font.PLAIN, 11));
        ch1Check.setForeground(new Color(50, 220, 80));
        ch2Check.setForeground(new Color(80, 180, 255));
        ch1Check.setToolTipText("Show or hide Channel 1 in graph and log");
        ch2Check.setToolTipText("Show or hide Channel 2 in graph and log");
        ch1Check.setEnabled(false);
        ch2Check.setEnabled(false);

        ch1Check.addItemListener(e -> handleChannelToggle(0));
        ch2Check.addItemListener(e -> handleChannelToggle(1));

        // --- pause button ---
        pauseButton = new JButton("Pause");
        pauseButton.setToolTipText("Pause terminal output");
        pauseButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        pauseButton.setMargin(new Insets(1, 5, 1, 5));
        pauseButton.setFocusPainted(false);
        pauseButton.addActionListener(e -> togglePause());

        // Row 1: Show checkboxes (left) + Pause button (right)
        JPanel showPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JLabel showLabel = new JLabel("Show:");
        showLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        showPanel.add(showLabel);
        showPanel.add(ch1Check);
        showPanel.add(ch2Check);

        JPanel controlsRow = new JPanel(new BorderLayout());
        controlsRow.add(showPanel, BorderLayout.WEST);
        controlsRow.add(pauseButton, BorderLayout.EAST);

        // Row 2: Ch1 readings + Ch1 mode (always visible)
        JPanel ch1ReadingsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        ch1ReadingsRow.add(rawLabel);
        ch1ReadingsRow.add(voltageLabel);
        ch1ReadingsRow.add(ch1ModeLabel);

        // Row 3: Ch2 readings + Ch2 mode (always visible)
        JPanel ch2ReadingsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        ch2ReadingsRow.add(ch2RawLabel);
        ch2ReadingsRow.add(ch2VoltageLabel);
        ch2ReadingsRow.add(ch2ModeLabel);

        JPanel readingsStack = new JPanel();
        readingsStack.setLayout(new BoxLayout(readingsStack, BoxLayout.Y_AXIS));
        readingsStack.add(controlsRow);
        readingsStack.add(ch1ReadingsRow);
        readingsStack.add(ch2ReadingsRow);

        strip.add(readingsStack, BorderLayout.CENTER);
        add(strip, BorderLayout.NORTH);

        dataDisplay = new JTextArea();
        dataDisplay.setEditable(false);
        dataDisplay.setFont(new Font("Monospaced", Font.PLAIN, 12));
        dataDisplay.setBackground(new Color(18, 18, 18));
        dataDisplay.setForeground(new Color(200, 230, 200));
        dataDisplay.setCaretColor(Color.GREEN);

        JScrollPane scroll = new JScrollPane(dataDisplay);
        scroll.setPreferredSize(new Dimension(480, 380));
        add(scroll, BorderLayout.CENTER);

        setDualChannelAvailable(false);
    }

    void setChannelVisibilityListener(BiConsumer<Integer, Boolean> listener) {
        this.visibilityListener = listener != null ? listener : (channel, visible) -> {};
        this.visibilityListener.accept(0, ch1Check.isSelected());
        this.visibilityListener.accept(1, ch2Check.isSelected());
    }

    void setConnected(boolean connected) {
        ch1Check.setEnabled(connected);
        if (!connected) {
            ch2Check.setEnabled(false);
        }
    }

    void setDualChannelAvailable(boolean available) {
        ch2Check.setEnabled(available);
        if (!available) {
            setChannelSelected(1, false);
        } else {
            // Ch2 just became live — it starts in NORMAL mode by definition
            // (injection cannot have started before the channel was revealed).
            ch2ModeLabel.setText("Mode: NORMAL");
            ch2ModeLabel.setForeground(new Color(40, 160, 40));
        }
    }

    void setChannelSelected(int channel, boolean selected) {
        AbstractButton button = channel == 0 ? ch1Check : ch2Check;
        updatingChannelFilter = true;
        button.setSelected(selected);
        updatingChannelFilter = false;
        visibilityListener.accept(channel, button.isSelected());
        rebuildLogDisplay();
    }

    void setPrimaryReading(int raw, double volts) {
        rawLabel.setText("Ch1 ADC: " + raw);
        voltageLabel.setText(String.format("Ch1: %.3f V", volts));
    }

    void setSecondaryReading(int raw, double volts) {
        ch2RawLabel.setText("Ch2 ADC: " + raw);
        ch2VoltageLabel.setText(String.format("Ch2: %.3f V", volts));
    }

    void setModeNormal() {
        ch1ModeLabel.setText("Mode: NORMAL");
        ch1ModeLabel.setForeground(new Color(40, 160, 40));
        if (ch2Check.isEnabled()) {
            ch2ModeLabel.setText("Mode: NORMAL");
            ch2ModeLabel.setForeground(new Color(40, 160, 40));
        }
    }

    void setModeInjecting(int channel) {
        JLabel label = channel == 0 ? ch1ModeLabel : ch2ModeLabel;
        label.setText("Mode: INJECTING");
        label.setForeground(new Color(220, 120, 0));
    }

    void clearMode() {
        Color defaultFg = UIManager.getColor("Label.foreground");
        ch1ModeLabel.setText("Mode: -");
        ch1ModeLabel.setForeground(defaultFg);
        ch2ModeLabel.setText("Mode: -");
        ch2ModeLabel.setForeground(defaultFg);
    }

    void resetReadings() {
        rawLabel.setText("Ch1 ADC: -");
        voltageLabel.setText("Ch1: - V");
        ch2RawLabel.setText("Ch2 ADC: -");
        ch2VoltageLabel.setText("Ch2: - V");
        clearMode();
        setDualChannelAvailable(false);
    }

    void appendSystemLog(String text) {
        addLogEntry(text, LOG_SYSTEM);
    }

    void appendChannelLog(String text, int channel) {
        addLogEntry(text, channel);
    }

    private void addLogEntry(String text, int channel) {
        logEntries.add(new LogEntry(text, channel));
        if (logEntries.size() > MAX_LOG_ENTRIES) {
            logEntries.remove(0);
        }
        if (!displayPaused && isLogChannelVisible(channel)) {
            dataDisplay.append(text + "\n");
            trimLog();
            dataDisplay.setCaretPosition(dataDisplay.getDocument().getLength());
        }
    }

    private boolean isLogChannelVisible(int channel) {
        if (channel == LOG_SYSTEM) {
            return true;
        }
        if (channel == 0) {
            return ch1Check.isSelected();
        }
        if (channel == 1) {
            return ch2Check.isSelected();
        }
        return true;
    }

    private void rebuildLogDisplay() {
        StringBuilder builder = new StringBuilder();
        for (LogEntry entry : logEntries) {
            if (isLogChannelVisible(entry.channel())) {
                builder.append(entry.text()).append('\n');
            }
        }
        dataDisplay.setText(builder.toString());
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

    private void togglePause() {
        if (!displayPaused) {
            appendSystemLog("-- Output paused --");
            displayPaused = true;
            pauseButton.setText("Resume");
            pauseButton.setToolTipText("Resume terminal output");
            return;
        }

        displayPaused = false;
        pauseButton.setText("Pause");
        pauseButton.setToolTipText("Pause terminal output");
        rebuildLogDisplay();
        appendSystemLog("-- Output resumed --");
    }

    private void handleChannelToggle(int channel) {
        if (updatingChannelFilter) {
            return;
        }

        if (!ch1Check.isSelected() && !ch2Check.isSelected()) {
            updatingChannelFilter = true;
            if (channel == 0) {
                ch1Check.setSelected(true);
            } else {
                ch2Check.setSelected(true);
            }
            updatingChannelFilter = false;
            return;
        }

        visibilityListener.accept(0, ch1Check.isSelected());
        visibilityListener.accept(1, ch2Check.isSelected());
        rebuildLogDisplay();
    }

    private static JLabel makeSummaryLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Monospaced", Font.BOLD, 13));
        return label;
    }
}
