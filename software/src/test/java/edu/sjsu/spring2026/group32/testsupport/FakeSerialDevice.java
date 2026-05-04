package edu.sjsu.spring2026.group32.testsupport;

import edu.sjsu.spring2026.group32.hardware.serial.SerialDevice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link SerialDevice} backed by a {@link FakeSerialFirmware}.
 */
public final class FakeSerialDevice implements SerialDevice {
    private final String systemPortName;
    private final String descriptivePortName;
    private final FakeSerialFirmware firmware;

    private final List<String> emittedLines = new CopyOnWriteArrayList<>();
    private int baudRate;
    private int timeoutMode;
    private int readTimeout;
    private int writeTimeout;
    private boolean open;

    private DeviceInputStream inputStream;
    private FirmwareCommandOutputStream outputStream;

    public FakeSerialDevice(String systemPortName, String descriptivePortName, FakeSerialFirmware firmware) {
        this.systemPortName = systemPortName;
        this.descriptivePortName = descriptivePortName;
        this.firmware = firmware;
        resetStreams();
    }

    public FakeSerialFirmware getFirmware() {
        return firmware;
    }

    public List<String> getEmittedLines() {
        return List.copyOf(emittedLines);
    }

    public String getLastEmittedLine() {
        return emittedLines.isEmpty() ? null : emittedLines.get(emittedLines.size() - 1);
    }

    public List<String> getReceivedCommands() {
        return firmware.getReceivedCommands();
    }

    public String getLastReceivedCommand() {
        return firmware.getLastReceivedCommand();
    }

    public void emitLine(String line) {
        if (!open) {
            return;
        }
        emittedLines.add(line);
        inputStream.appendLine(line);
    }

    public void emitLines(List<String> lines) {
        for (String line : lines) {
            emitLine(line);
        }
    }

    public void simulateDisconnect() {
        if (!open) {
            return;
        }
        open = false;
        inputStream.closeFromDevice();
        outputStream.closeFromDevice();
    }

    public void simulateReconnect() {
        if (open) {
            return;
        }
        resetStreams();
        open = true;
        emitLines(firmware.reconnectSequence());
    }

    public int getBaudRate() {
        return baudRate;
    }

    public int getTimeoutMode() {
        return timeoutMode;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getWriteTimeout() {
        return writeTimeout;
    }

    @Override
    public String getDescriptivePortName() {
        return descriptivePortName;
    }

    @Override
    public String getSystemPortName() {
        return systemPortName;
    }

    @Override
    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate;
    }

    @Override
    public void setComPortTimeouts(int timeoutMode, int readTimeout, int writeTimeout) {
        this.timeoutMode = timeoutMode;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
    }

    @Override
    public boolean openPort() {
        if (open) {
            return true;
        }
        resetStreams();
        open = true;
        emitLines(firmware.bootSequence());
        return true;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void closePort() {
        simulateDisconnect();
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    private void resetStreams() {
        this.inputStream = new DeviceInputStream();
        this.outputStream = new FirmwareCommandOutputStream();
    }

    private final class FirmwareCommandOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean closed;

        @Override
        public synchronized void write(int b) throws IOException {
            if (closed) {
                throw new IOException("Stream is closed");
            }
            if (b == '\n' || b == '\r') {
                flushBuffer();
            } else {
                buffer.write(b);
            }
        }

        @Override
        public synchronized void flush() throws IOException {
            flushBuffer();
        }

        @Override
        public synchronized void close() throws IOException {
            closed = true;
            flushBuffer();
        }

        synchronized void closeFromDevice() {
            closed = true;
        }

        private void flushBuffer() {
            if (buffer.size() == 0) {
                return;
            }
            String command = buffer.toString(StandardCharsets.UTF_8);
            buffer.reset();
            if (!command.isBlank()) {
                emitLines(firmware.handleCommand(command));
            }
        }
    }

    private static final class DeviceInputStream extends InputStream {
        private final List<Byte> bytes = new ArrayList<>();
        private int readIndex;
        private boolean closed;

        @Override
        public synchronized int read() {
            while (readIndex >= bytes.size()) {
                if (closed) {
                    return -1;
                }
                try {
                    wait(50L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            }
            return bytes.get(readIndex++) & 0xFF;
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) throws IOException {
            int first = read();
            if (first == -1) return -1;
            b[off] = (byte) first;
            int i = 1;
            while (i < len && readIndex < bytes.size()) {
                b[off + i++] = (byte) (bytes.get(readIndex++) & 0xFF);
            }
            return i;
        }

        synchronized void appendLine(String line) {
            byte[] content = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            for (byte b : content) {
                bytes.add(b);
            }
            notifyAll();
        }

        synchronized void closeFromDevice() {
            closed = true;
            notifyAll();
        }
    }
}
