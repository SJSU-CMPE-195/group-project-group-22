package edu.sjsu.spring2026.group32.testsupport;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.serial.SerialDevice;

import java.util.function.Supplier;

/**
 * Bundles a fake serial device, its firmware model, and a manager under test.
 */
public final class SerialTestRig {
    private final FakeSerialFirmware firmware;
    private final FakeSerialDevice device;
    private final Supplier<SerialDevice[]> portSupplier;
    private final SerialConnectionManager manager;

    private SerialTestRig(FakeSerialFirmware firmware,
                          FakeSerialDevice device,
                          Supplier<SerialDevice[]> portSupplier,
                          SerialConnectionManager manager) {
        this.firmware = firmware;
        this.device = device;
        this.portSupplier = portSupplier;
        this.manager = manager;
    }

    public static SerialTestRig createSingleChannelRig() {
        return createRig(1, "COM_HTZ", "CP210 NeuralSignal");
    }

    public static SerialTestRig createDualChannelRig() {
        return createRig(2, "COM_PONG", "CP210 NeuralSignal Dual");
    }

    public static SerialTestRig createRig(int channelCount, String portName, String descriptiveName) {
        FakeSerialFirmware firmware = new FakeSerialFirmware(channelCount);
        FakeSerialDevice device = new FakeSerialDevice(portName, descriptiveName, firmware);
        Supplier<SerialDevice[]> supplier = () -> new SerialDevice[]{device};
        SerialConnectionManager manager = new SerialConnectionManager(supplier, 50);
        return new SerialTestRig(firmware, device, supplier, manager);
    }

    public FakeSerialFirmware firmware() {
        return firmware;
    }

    public FakeSerialDevice device() {
        return device;
    }

    public Supplier<SerialDevice[]> portSupplier() {
        return portSupplier;
    }

    public SerialConnectionManager manager() {
        return manager;
    }

    public boolean connectManagerToFakeDevice() {
        return manager.connectTo(device);
    }

    public void pushIncomingLine(String line) {
        device.emitLine(line);
    }

    public String readLastOutgoingCommand() {
        return device.getLastReceivedCommand();
    }

    public void disconnectAndReconnectDevice() {
        device.simulateDisconnect();
        device.simulateReconnect();
    }
}
