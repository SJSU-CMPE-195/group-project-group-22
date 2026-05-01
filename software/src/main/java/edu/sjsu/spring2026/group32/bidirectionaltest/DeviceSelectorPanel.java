package edu.sjsu.spring2026.group32.bidirectionaltest;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.launcher.ConnectionStatusPanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

final class DeviceSelectorPanel extends JPanel {
    private final JRadioButton htzRadio;
    private final JRadioButton pongRadio;
    private final SerialConnectionManager htzManager;
    private final SerialConnectionManager pongManager;
    private final SerialConnectionManager.SerialListener htzListener;
    private final SerialConnectionManager.SerialListener pongListener;

    DeviceSelectorPanel(SerialConnectionManager htzManager,
                        SerialConnectionManager pongManager,
                        Runnable onHtzSelected,
                        Runnable onPongSelected) {
        super(new FlowLayout(FlowLayout.LEFT, 12, 4));
        setBorder(new TitledBorder("Device (connections managed by Launcher)"));
        this.htzManager = htzManager;
        this.pongManager = pongManager;

        htzRadio = new JRadioButton("HTZ - 3-neuron (single ch)");
        pongRadio = new JRadioButton("Pong - 6-neuron (dual ch)");

        ButtonGroup group = new ButtonGroup();
        group.add(htzRadio);
        group.add(pongRadio);

        htzRadio.setEnabled(htzManager != null && htzManager.isConnected());
        pongRadio.setEnabled(pongManager != null && pongManager.isConnected());

        htzListener = new SerialConnectionManager.SerialListener() {
            @Override
            public void onConnected(String portName) {
                SwingUtilities.invokeLater(() -> htzRadio.setEnabled(true));
            }

            @Override
            public void onDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> {
                    htzRadio.setEnabled(false);
                    htzRadio.setSelected(false);
                });
            }
        };
        pongListener = new SerialConnectionManager.SerialListener() {
            @Override
            public void onConnected(String portName) {
                SwingUtilities.invokeLater(() -> pongRadio.setEnabled(true));
            }

            @Override
            public void onDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> {
                    pongRadio.setEnabled(false);
                    pongRadio.setSelected(false);
                });
            }
        };

        if (htzManager != null) {
            htzManager.addListener(htzListener);
        }
        if (pongManager != null) {
            pongManager.addListener(pongListener);
        }

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

    @Override
    public void removeNotify() {
        if (htzManager != null) {
            htzManager.removeListener(htzListener);
        }
        if (pongManager != null) {
            pongManager.removeListener(pongListener);
        }
        super.removeNotify();
    }

    void selectHtz() {
        htzRadio.setSelected(true);
    }

    void selectPong() {
        pongRadio.setSelected(true);
    }
}
