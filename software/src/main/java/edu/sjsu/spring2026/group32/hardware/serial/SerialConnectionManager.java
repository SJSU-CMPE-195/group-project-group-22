package edu.sjsu.spring2026.group32.hardware.serial;

import java.util.Scanner;
import java.util.function.Supplier;

public class SerialConnectionManager {
    private SerialDevice comPort;
    private Scanner scanner;
    private static final int BAUD_RATE = 115200;

    private final Supplier<SerialDevice[]> portProvider;

    public SerialConnectionManager(Supplier<SerialDevice[]> portProvider) {
        this.portProvider = portProvider;
    }

    public boolean connect() {
        SerialDevice[] ports;
        try {
            ports = portProvider.get();
        } catch (Exception | Error e) {
            System.err.println(">>> Failed to enumerate serial ports: " + e.getMessage());
            System.err.println(">>> Hardware player will not score (no serial ports available).");
            return false;
        }

        for (SerialDevice port : ports) {
            String name = port.getDescriptivePortName();
            if (name.contains("CP210") || name.contains("CH340") || name.contains("USB-to-Serial")) {
                this.comPort = port;
                this.comPort.setBaudRate(BAUD_RATE);
                // 1 = TIMEOUT_READ_SEMI_BLOCKING. Hardcoded to avoid jSerialComm import bleeding.
                this.comPort.setComPortTimeouts(1, 100, 0);

                if (this.comPort.openPort()) {
                    System.out.println(">>> Serial Connection Established: " + port.getSystemPortName());
                    this.scanner = new Scanner(comPort.getInputStream());
                    return true;
                }
            }
        }
        System.err.println("CRITICAL: Compatible serial hardware not detected.");
        return false;
    }

    public String getNextLine() {
        if (scanner != null && scanner.hasNextLine()) {
            return scanner.nextLine();
        }
        return null; // Return null if no data is ready, letting the parser handle it
    }

    public boolean isConnected() {
        return comPort != null && comPort.isOpen();
    }

    public void disconnect() {
        if (scanner != null) scanner.close();
        if (comPort != null && comPort.isOpen()) {
            comPort.closePort();
            System.out.println(">>> Serial Connection Closed.");
        }
    }
}
