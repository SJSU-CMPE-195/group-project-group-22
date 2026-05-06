package edu.sjsu.spring2026.group32.launcher.ui;

import edu.sjsu.spring2026.group32.hardware.NeuralHardwareConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;

/**
 * Reusable launcher button that edits gameplay-only neural hardware voltages.
 */
public class LauncherHardwareSettingsButton extends JButton {
    private static final Dimension BUTTON_SIZE = new Dimension(26, 26);
    private static final NumberFormat VOLTAGE_FORMAT = new DecimalFormat("0.00");

    private int channelCount;
    private boolean settingsLocked;
    private JDialog activeDialog;

    public LauncherHardwareSettingsButton() {
        super("⚙");
        setFocusPainted(false);
        setFont(new Font("SansSerif", Font.PLAIN, 14));
        setMargin(new Insets(0, 0, 0, 0));
        setPreferredSize(BUTTON_SIZE);
        setMinimumSize(BUTTON_SIZE);
        setMaximumSize(BUTTON_SIZE);
        setToolTipText("Gameplay hardware settings");
        addActionListener(e -> openDialog());
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
        refreshEnabledState();
    }

    public void setSettingsLocked(boolean settingsLocked) {
        this.settingsLocked = settingsLocked;
        if (settingsLocked) {
            closeActiveDialog();
        }
        refreshEnabledState();
    }

    private void openDialog() {
        if (channelCount <= 0 || settingsLocked) {
            return;
        }

        JFormattedTextField[] fields = buildFields();
        JPanel panel = buildDialogPanel(fields);

        JOptionPane optionPane = new JOptionPane(
                panel,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(
                SwingUtilities.getWindowAncestor(this),
                "Gameplay Hardware Settings");
        activeDialog = dialog;
        dialog.setModal(true);
        dialog.setVisible(true);
        activeDialog = null;

        Object value = optionPane.getValue();
        if (!settingsLocked && value instanceof Integer result && result == JOptionPane.OK_OPTION) {
            applyFields(fields);
        }
    }

    private JFormattedTextField[] buildFields() {
        if (channelCount == 1) {
            return new JFormattedTextField[]{
                    voltageField(NeuralHardwareConfig.getHitTheZoneInjectionVoltage()),
                    voltageField(NeuralHardwareConfig.getHitTheZoneThresholdVoltage())
            };
        }

        return new JFormattedTextField[]{
                voltageField(NeuralHardwareConfig.getPongLeftInjectionVoltage()),
                voltageField(NeuralHardwareConfig.getPongLeftThresholdVoltage()),
                voltageField(NeuralHardwareConfig.getPongRightInjectionVoltage()),
                voltageField(NeuralHardwareConfig.getPongRightThresholdVoltage())
        };
    }

    private JPanel buildDialogPanel(JFormattedTextField[] fields) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel description = new JLabel(
                "<html>These voltages are used by Hit The Zone and Pong hardware AI only.<br>"
                        + "They do not change manual injection controls in Bidirectional Test.<br><br>"
                        + "Range: 0.0 - 3.3 V<br>"
                        + "Recommended: Injection 3.0 V and Threshold 0.5 V or higher.</html>");
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(description);
        panel.add(Box.createVerticalStrut(10));

        if (channelCount == 1) {
            panel.add(buildChannelPanel("Channel 1 (Hit The Zone)", fields[0], fields[1]));
        } else {
            panel.add(buildChannelPanel("Channel 1 (Pong Left)", fields[0], fields[1]));
            panel.add(Box.createVerticalStrut(8));
            panel.add(buildChannelPanel("Channel 2 (Pong Right)", fields[2], fields[3]));
        }

        return panel;
    }

    private JPanel buildChannelPanel(String title,
                                     JFormattedTextField injectionField,
                                     JFormattedTextField thresholdField) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createTitledBorder(title));

        panel.add(buildVoltageRow("Injection (V):", injectionField));
        panel.add(Box.createVerticalStrut(6));
        panel.add(buildVoltageRow("Threshold (V):", thresholdField));
        return panel;
    }

    private JComponent buildVoltageRow(String labelText, JFormattedTextField field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(labelText);
        row.add(label);
        row.add(field);
        return row;
    }

    private JFormattedTextField voltageField(double value) {
        JFormattedTextField field = new JFormattedTextField(VOLTAGE_FORMAT);
        field.setValue(value);
        field.setColumns(5);
        field.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return field;
    }

    private void applyFields(JFormattedTextField[] fields) {
        if (channelCount == 1) {
            NeuralHardwareConfig.setHitTheZoneInjectionVoltage(readVoltage(fields[0]));
            NeuralHardwareConfig.setHitTheZoneThresholdVoltage(readVoltage(fields[1]));
            return;
        }

        NeuralHardwareConfig.setPongLeftInjectionVoltage(readVoltage(fields[0]));
        NeuralHardwareConfig.setPongLeftThresholdVoltage(readVoltage(fields[1]));
        NeuralHardwareConfig.setPongRightInjectionVoltage(readVoltage(fields[2]));
        NeuralHardwareConfig.setPongRightThresholdVoltage(readVoltage(fields[3]));
    }

    private double readVoltage(JFormattedTextField field) {
        try {
            field.commitEdit();
            Object value = field.getValue();
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return Double.parseDouble(field.getText().trim());
        } catch (ParseException e) {
            throw new IllegalArgumentException("Enter a valid voltage between 0.0 and 3.3 V.", e);
        }
    }

    private void refreshEnabledState() {
        setEnabled(channelCount > 0 && !settingsLocked);
    }

    private void closeActiveDialog() {
        if (activeDialog != null) {
            activeDialog.dispose();
            activeDialog = null;
        }
    }
}
