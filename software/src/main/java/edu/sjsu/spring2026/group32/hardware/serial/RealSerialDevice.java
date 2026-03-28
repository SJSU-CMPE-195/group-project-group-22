package edu.sjsu.spring2026.group32.hardware.serial;

import com.fazecast.jSerialComm.SerialPort;
import java.io.InputStream;
import java.io.OutputStream;

public class RealSerialDevice implements SerialDevice {
    private final SerialPort port;

    public RealSerialDevice(SerialPort port) {
        this.port = port;
    }

    @Override
    public String getDescriptivePortName() { return port.getDescriptivePortName(); }

    @Override
    public String getSystemPortName() { return port.getSystemPortName(); }

    @Override
    public void setBaudRate(int baudRate) { port.setBaudRate(baudRate); }

    @Override
    public void setComPortTimeouts(int timeoutMode, int readTimeout, int writeTimeout) {
        port.setComPortTimeouts(timeoutMode, readTimeout, writeTimeout);
    }

    @Override
    public boolean openPort() { return port.openPort(); }

    @Override
    public boolean isOpen() { return port.isOpen(); }

    @Override
    public void closePort() { port.closePort(); }

    @Override
    public InputStream getInputStream() { return port.getInputStream(); }

    @Override
    public OutputStream getOutputStream() { return port.getOutputStream(); }

    // Helper to get all real ports wrapped in our interface
    // Returns an empty array if the jSerialComm native library fails to load
    // (e.g. jSerialComm.dll access denied on Windows), allowing the rest of
    // the application to start and run without serial hardware.
    public static SerialDevice[] getRealPorts() {
        try {
            SerialPort[] realPorts = SerialPort.getCommPorts();
            SerialDevice[] wrappedPorts = new SerialDevice[realPorts.length];
            for (int i = 0; i < realPorts.length; i++) {
                wrappedPorts[i] = new RealSerialDevice(realPorts[i]);
            }
            return wrappedPorts;
        } catch (Exception | Error e) {
            System.err.println(">>> Failed to enumerate serial ports (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            System.err.println(">>> Hardware player will not score (no serial ports available).");
            return new SerialDevice[0];
        }
    }
}