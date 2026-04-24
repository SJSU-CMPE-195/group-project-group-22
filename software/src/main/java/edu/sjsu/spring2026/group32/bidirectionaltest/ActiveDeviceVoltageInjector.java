package edu.sjsu.spring2026.group32.bidirectionaltest;

import edu.sjsu.spring2026.group32.hardware.VoltageInjector;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;

import java.util.function.Supplier;

/**
 * Routes voltage-injection commands to whichever Launcher-managed device is
 * currently selected in the Bidirectional Test window.
 */
final class ActiveDeviceVoltageInjector implements VoltageInjector {
    private final Supplier<SerialConnectionManager> managerSupplier;

    ActiveDeviceVoltageInjector(Supplier<SerialConnectionManager> managerSupplier) {
        this.managerSupplier = managerSupplier;
    }

    @Override
    public void injectVoltage(int channel, double volts) {
        SerialConnectionManager manager = managerSupplier.get();
        if (manager == null || !manager.isConnected()) {
            return;
        }
        manager.sendLine(String.format("INJECT_V_CH%d:%.3f", channel, volts));
    }

    @Override
    public void stopInjection(int channel) {
        SerialConnectionManager manager = managerSupplier.get();
        if (manager == null || !manager.isConnected()) {
            return;
        }

        if (channel == 0) {
            manager.sendLine("STOP_INJECT");
            return;
        }

        manager.sendLine("STOP_INJECT_CH" + channel);
    }
}
