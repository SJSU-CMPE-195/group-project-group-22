package edu.sjsu.spring2026.group32.bidirectionaltest.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public final class InjectionChannelPanel extends JPanel {
    private final int channelNumber;
    private final JTextField voltageField;
    private final JSlider voltageSlider;
    private final JLabel sliderValueLabel;
    private final JButton injectButton;
    private final JButton stopButton;
    private final JRadioButton continuousRadio;
    private final JRadioButton intervalRadio;
    private final JSpinner onSpinner;
    private final JSpinner offSpinner;
    private final JSpinner repeatSpinner;
    private final JCheckBox infiniteCheck;
    private final JPanel intervalOptionsPanel;

    private boolean controlsEnabled = false;
    private boolean injecting = false;

    public InjectionChannelPanel(int channelNumber, Runnable onInject, Runnable onStop) {
        super(new BorderLayout(6, 4));
        this.channelNumber = channelNumber;
        setBorder(new TitledBorder("Voltage Injection - Channel " + channelNumber));

        JPanel sliderRow = new JPanel(new BorderLayout(6, 0));
        sliderRow.setBorder(new EmptyBorder(2, 4, 2, 4));

        voltageSlider = new JSlider(0, 330, 0);
        sliderValueLabel = new JLabel("0.00 V");
        sliderValueLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        sliderValueLabel.setPreferredSize(new Dimension(56, 20));

        voltageField = new JTextField("0.00", 7);
        voltageField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        voltageField.addActionListener(e -> syncSliderFromField(3.3));

        voltageSlider.addChangeListener(e -> {
            double volts = voltageSlider.getValue() / 100.0;
            sliderValueLabel.setText(String.format("%.2f V", volts));
            voltageField.setText(String.format("%.2f", volts));
        });

        sliderRow.add(new JLabel("0.00 V"), BorderLayout.WEST);
        sliderRow.add(voltageSlider, BorderLayout.CENTER);
        sliderRow.add(new JLabel("3.30 V"), BorderLayout.EAST);

        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        injectButton = new JButton("Inject");
        stopButton = new JButton("Stop");

        injectButton.setEnabled(false);
        stopButton.setEnabled(false);
        injectButton.setBackground(new Color(60, 120, 200));
        injectButton.setForeground(Color.WHITE);
        injectButton.setOpaque(true);
        stopButton.setBackground(new Color(190, 100, 30));
        stopButton.setForeground(Color.WHITE);
        stopButton.setOpaque(true);

        controlRow.add(new JLabel("Channel " + channelNumber + ":"));
        controlRow.add(new JLabel("Voltage (V):"));
        controlRow.add(voltageField);
        controlRow.add(sliderValueLabel);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(injectButton);
        controlRow.add(stopButton);

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        continuousRadio = new JRadioButton("Constant", true);
        intervalRadio = new JRadioButton("Interval");

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(continuousRadio);
        modeGroup.add(intervalRadio);

        onSpinner = new JSpinner(new SpinnerNumberModel(200, 10, 60000, 50));
        offSpinner = new JSpinner(new SpinnerNumberModel(300, 10, 60000, 50));
        repeatSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 9999, 1));
        infiniteCheck = new JCheckBox("Infinite");

        onSpinner.setPreferredSize(new Dimension(72, 26));
        offSpinner.setPreferredSize(new Dimension(72, 26));
        repeatSpinner.setPreferredSize(new Dimension(60, 26));

        infiniteCheck.addItemListener(e ->
                repeatSpinner.setEnabled(!infiniteCheck.isSelected()));

        intervalOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        intervalOptionsPanel.add(new JLabel("On:"));
        intervalOptionsPanel.add(onSpinner);
        intervalOptionsPanel.add(new JLabel("ms  Off:"));
        intervalOptionsPanel.add(offSpinner);
        intervalOptionsPanel.add(new JLabel("ms  Repeat:"));
        intervalOptionsPanel.add(repeatSpinner);
        intervalOptionsPanel.add(infiniteCheck);
        intervalOptionsPanel.setVisible(false);

        intervalRadio.addItemListener(e -> {
            boolean intervalMode = intervalRadio.isSelected();
            intervalOptionsPanel.setVisible(intervalMode);
            boolean inputsEnabled = controlsEnabled && !injecting;
            onSpinner.setEnabled(intervalMode && inputsEnabled);
            offSpinner.setEnabled(intervalMode && inputsEnabled);
            infiniteCheck.setEnabled(intervalMode && inputsEnabled);
            repeatSpinner.setEnabled(intervalMode && inputsEnabled && !infiniteCheck.isSelected());
        });

        modeRow.add(new JLabel("Mode:"));
        modeRow.add(continuousRadio);
        modeRow.add(intervalRadio);
        modeRow.add(intervalOptionsPanel);

        JPanel inner = new JPanel(new BorderLayout(0, 2));
        inner.add(sliderRow, BorderLayout.NORTH);
        inner.add(controlRow, BorderLayout.CENTER);
        inner.add(modeRow, BorderLayout.SOUTH);
        add(inner, BorderLayout.CENTER);

        injectButton.addActionListener(e -> onInject.run());
        stopButton.addActionListener(e -> onStop.run());
    }

    public Double readVoltage(Component parent, double maxVoltage) {
        syncSliderFromField(maxVoltage);
        double volts;
        try {
            volts = Double.parseDouble(voltageField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Enter a number between 0.00 and 3.30.",
                    "Invalid Input",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        if (volts < 0.0 || volts > maxVoltage) {
            JOptionPane.showMessageDialog(parent,
                    String.format("Voltage must be 0.00 - %.2f V.", maxVoltage),
                    "Out of Range",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        return volts;
    }

    void syncSliderFromField(double maxVoltage) {
        try {
            double volts = Double.parseDouble(voltageField.getText().trim());
            volts = Math.max(0.0, Math.min(maxVoltage, volts));
            voltageSlider.setValue((int) Math.round(volts * 100));
            sliderValueLabel.setText(String.format("%.2f V", volts));
            voltageField.setText(String.format("%.2f", volts));
        } catch (NumberFormatException ignored) {
        }
    }

    public boolean isContinuousMode() {
        return continuousRadio.isSelected();
    }

    public boolean isIntervalMode() {
        return intervalRadio.isSelected();
    }

    public int getOnMs() {
        return (int) onSpinner.getValue();
    }

    public int getOffMs() {
        return (int) offSpinner.getValue();
    }

    public int getRepeatCount() {
        return (int) repeatSpinner.getValue();
    }

    public boolean isInfinite() {
        return infiniteCheck.isSelected();
    }

    public void setControlsEnabled(boolean enabled) {
        controlsEnabled = enabled;
        if (!enabled) {
            injecting = false;
        }
        refreshButtonStates();
        voltageField.setEnabled(enabled);
        voltageSlider.setEnabled(enabled);
        continuousRadio.setEnabled(enabled);
        intervalRadio.setEnabled(enabled);
        onSpinner.setEnabled(enabled && isIntervalMode());
        offSpinner.setEnabled(enabled && isIntervalMode());
        infiniteCheck.setEnabled(enabled && isIntervalMode());
        repeatSpinner.setEnabled(enabled && isIntervalMode() && !isInfinite());
    }

    public void setInjecting(boolean active) {
        injecting = active;
        refreshButtonStates();
        boolean inputsEnabled = controlsEnabled && !injecting;
        voltageField.setEnabled(inputsEnabled);
        voltageSlider.setEnabled(inputsEnabled);
        continuousRadio.setEnabled(inputsEnabled);
        intervalRadio.setEnabled(inputsEnabled);
        onSpinner.setEnabled(inputsEnabled && isIntervalMode());
        offSpinner.setEnabled(inputsEnabled && isIntervalMode());
        infiniteCheck.setEnabled(inputsEnabled && isIntervalMode());
        repeatSpinner.setEnabled(inputsEnabled && isIntervalMode() && !isInfinite());
    }

    private void refreshButtonStates() {
        injectButton.setEnabled(controlsEnabled && !injecting);
        stopButton.setEnabled(controlsEnabled && injecting);
    }

    int getChannelNumber() {
        return channelNumber;
    }
}
