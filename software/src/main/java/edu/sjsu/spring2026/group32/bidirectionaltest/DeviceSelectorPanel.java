package edu.sjsu.spring2026.group32.bidirectionaltest;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.launcher.ConnectionStatusPanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

final class DeviceSelectorPanel extends JPanel {
    private final JRadioButton htzRadio;
    private final JRadioButton pongRadio;

    DeviceSelectorPanel(SerialConnectionManager htzManager,
                        SerialConnectionManager pongManager,
                        Runnable onHtzSelected,
                        Runnable onPongSelected) {
        super(new FlowLayout(FlowLayout.LEFT, 12, 4));
        setBorder(new TitledBorder("Device (connections managed by Launcher)"));

        htzRadio = new JRadioButton("HTZ - 3-neuron (single ch)");
        pongRadio = new JRadioButton("Pong - 6-neuron (dual ch)");

        ButtonGroup group = new ButtonGroup();
        group.add(htzRadio);
        group.add(pongRadio);

        htzRadio.setEnabled(htzManager != null && htzManager.isConnected());
        pongRadio.setEnabled(pongManager != null && pongManager.isConnected());

        htzRadio.addActionListener(e -> {
            if (htzManager != null && htzManager.isConnected()) {
                onHtzSelected.run();
            }
        });
        pongRadio.addActionListener(e -> {
            if (pongManager != null && pongManager.isConnected()) {
                onPongSelected.run();
            }
        });

        ConnectionStatusPanel statusPanel = new ConnectionStatusPanel();
        statusPanel.addDevice("HTZ", htzManager);
        statusPanel.addDevice("Pong", pongManager);

        add(htzRadio);
        add(Box.createHorizontalStrut(20));
        add(pongRadio);
        add(Box.createHorizontalStrut(20));
        add(statusPanel);
    }

    void selectHtz() {
        htzRadio.setSelected(true);
    }

    void selectPong() {
        pongRadio.setSelected(true);
    }
}
