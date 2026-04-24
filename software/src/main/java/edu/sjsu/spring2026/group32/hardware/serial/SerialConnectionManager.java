package edu.sjsu.spring2026.group32.hardware.serial;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class SerialConnectionManager {
    public record SampleFrame(String line,
                              long millis,
                              int primaryRaw,
                              Integer secondaryRaw) {}

    public interface SerialListener {
        default void onSample(SampleFrame frame) {}
        default void onSampleLine(String line) {}
        default void onStatusPayload(String payload) {}
        default void onInfoUpdated(String deviceName, int channelCount) {}
        default void onDisconnected(String reason) {}
    }

    private static final int BAUD_RATE = 115200;
    private static final long RX_TIMEOUT_MS = 3_000;
    private static final long INFO_WAIT_SLICE_MS = 25;

    private final Supplier<SerialDevice[]> portProvider;
    private final int readTimeoutMs;
    private final List<SerialListener> listeners = new CopyOnWriteArrayList<>();
    private final Object infoMonitor = new Object();

    private SerialDevice comPort;
    private PrintWriter lineWriter;
    private ExecutorService readerThread;
    private volatile boolean readerRunning = false;

    private volatile long lastRxMs = 0;
    private volatile String latestDataLine;
    private volatile SampleFrame latestSampleFrame;
    private volatile String deviceName = "";
    private volatile int deviceChannelCount = 0;
    private volatile long infoUpdateCount = 0;

    public SerialConnectionManager(Supplier<SerialDevice[]> portProvider) {
        this(portProvider, 100);
    }

    public SerialConnectionManager(Supplier<SerialDevice[]> portProvider, int readTimeoutMs) {
        this.portProvider = portProvider;
        this.readTimeoutMs = readTimeoutMs;
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
                if (openConfiguredPort(port)) {
                    return true;
                }
            }
        }
        System.err.println("CRITICAL: Compatible serial hardware not detected.");
        return false;
    }

    public boolean connectTo(SerialDevice port) {
        return openConfiguredPort(port);
    }

    private synchronized boolean openConfiguredPort(SerialDevice port) {
        disconnectInternal(false, null, false);

        this.comPort = port;
        this.comPort.setBaudRate(BAUD_RATE);
        this.comPort.setComPortTimeouts(1, readTimeoutMs, 0);

        if (!this.comPort.openPort()) {
            System.err.println(">>> Failed to open port: " + port.getSystemPortName());
            this.comPort = null;
            return false;
        }

        System.out.println(">>> Serial Connection Established: " + port.getSystemPortName());
        latestDataLine = null;
        latestSampleFrame = null;
        clearHeartbeat();
        deviceName = "";
        deviceChannelCount = 0;
        infoUpdateCount = 0;
        initWriter();
        startReader();
        return true;
    }

    private void initWriter() {
        java.io.OutputStream out = comPort.getOutputStream();
        if (out != null) {
            this.lineWriter = new PrintWriter(
                    new BufferedWriter(new OutputStreamWriter(out)), true);
        }
    }

    private synchronized void startReader() {
        stopReaderThread();
        if (comPort == null) {
            return;
        }

        readerRunning = true;
        readerThread = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SerialConnectionManager-Reader");
            thread.setDaemon(true);
            return thread;
        });

        SerialDevice activePort = comPort;
        readerThread.execute(() -> runReaderLoop(activePort));
    }

    private void runReaderLoop(SerialDevice activePort) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(activePort.getInputStream()))) {
            while (readerRunning && activePort == comPort && activePort.isOpen()) {
                String line = reader.readLine();
                if (line == null) {
                    continue;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                refreshHeartbeat();
                dispatchIncomingLine(line);
            }
        } catch (IOException e) {
            if (readerRunning && activePort == comPort) {
                System.err.println(">>> Serial read exception: " + e.getMessage());
                disconnectInternal(false, e.getMessage(), true);
            }
        } catch (Exception e) {
            if (readerRunning && activePort == comPort) {
                System.err.println(">>> Serial reader failed: " + e.getMessage());
                disconnectInternal(false, e.getMessage(), true);
            }
        }
    }

    private void dispatchIncomingLine(String line) {
        if (line.startsWith("#INFO:")) {
            parseInfo(line);
            return;
        }

        if (line.startsWith("STATUS,")) {
            String payload = line.substring(7);
            for (SerialListener listener : listeners) {
                listener.onStatusPayload(payload);
            }
            return;
        }

        SampleFrame sampleFrame = parseSampleFrame(line);
        if (sampleFrame == null) {
            return;
        }

        latestDataLine = line;
        latestSampleFrame = sampleFrame;
        for (SerialListener listener : listeners) {
            listener.onSample(sampleFrame);
            listener.onSampleLine(line);
        }
    }

    private SampleFrame parseSampleFrame(String line) {
        String[] parts = line.split(",");
        if (parts.length < 4) {
            return null;
        }

        try {
            long millis = Long.parseLong(parts[0].trim());
            int primaryRaw = Integer.parseInt(parts[3].trim());
            Integer secondaryRaw = null;
            if (parts.length >= 5) {
                secondaryRaw = Integer.parseInt(parts[4].trim());
            }
            return new SampleFrame(line, millis, primaryRaw, secondaryRaw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void addListener(SerialListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(SerialListener listener) {
        listeners.remove(listener);
    }

    /**
     * Legacy polling API kept for game-loop callers.
     *
     * <p>Under the single-reader architecture this no longer consumes the
     * serial stream. It returns the latest sample line published by the
     * manager, or {@code null} if no sample has arrived yet.</p>
     */
    public String getNextLine() {
        return latestDataLine;
    }

    public SampleFrame getLatestSampleFrame() {
        return latestSampleFrame;
    }

    /**
     * Legacy raw-stream accessor retained for source compatibility.
     *
     * <p>The manager now owns the only supported serial read loop, so callers
     * should subscribe via {@link #addListener(SerialListener)} instead of
     * reading this stream directly.</p>
     */
    public InputStream getInputStream() {
        if (comPort == null) {
            return null;
        }
        return comPort.getInputStream();
    }

    public void sendLine(String command) {
        if (lineWriter != null) {
            lineWriter.println(command);
            lineWriter.flush();
        }
    }

    public void stopAllInjection() {
        sendLine("STOP_INJECT");
    }

    public long getLastRxMs() { return lastRxMs; }

    public boolean isRxTimedOut() {
        return lastRxMs > 0
                && (System.currentTimeMillis() - lastRxMs) > RX_TIMEOUT_MS;
    }

    public void refreshHeartbeat() {
        lastRxMs = System.currentTimeMillis();
    }

    public void clearHeartbeat() {
        lastRxMs = 0;
    }

    public String getDeviceName() { return deviceName; }

    public int getDeviceChannelCount() { return deviceChannelCount; }

    public boolean readInfoHandshake(int maxAttempts) {
        if (!isConnected()) {
            return false;
        }

        long startingInfoCount = infoUpdateCount;
        sendLine("INFO?");

        for (int i = 0; i < maxAttempts; i++) {
            synchronized (infoMonitor) {
                if (infoUpdateCount > startingInfoCount) {
                    return true;
                }
                try {
                    infoMonitor.wait(INFO_WAIT_SLICE_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return infoUpdateCount > startingInfoCount;
                }
            }
        }
        return infoUpdateCount > startingInfoCount;
    }

    private void parseInfo(String line) {
        String parsedDeviceName = "";
        int parsedChannelCount = 0;

        String body = line.substring(6);
        for (String part : body.split(",")) {
            part = part.trim();
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if ("CH".equals(kv[0].trim())) {
                    try {
                        parsedChannelCount = Integer.parseInt(kv[1].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else if (!part.isEmpty()) {
                parsedDeviceName = part;
            }
        }

        deviceName = parsedDeviceName;
        deviceChannelCount = parsedChannelCount;
        synchronized (infoMonitor) {
            infoUpdateCount++;
            infoMonitor.notifyAll();
        }
        for (SerialListener listener : listeners) {
            listener.onInfoUpdated(deviceName, deviceChannelCount);
        }
    }

    public boolean isConnected() {
        return comPort != null && comPort.isOpen();
    }

    public void disconnect() {
        disconnectInternal(true, null, true);
    }

    private synchronized void disconnectInternal(boolean gracefulStop,
                                                 String disconnectReason,
                                                 boolean notifyListeners) {
        if (gracefulStop && isConnected()) {
            stopAllInjection();
        }

        readerRunning = false;
        stopReaderThread();

        if (lineWriter != null) {
            lineWriter.close();
            lineWriter = null;
        }
        if (comPort != null) {
            comPort.closePort();
            comPort = null;
        }

        latestDataLine = null;
        latestSampleFrame = null;
        clearHeartbeat();
        deviceName = "";
        deviceChannelCount = 0;
        synchronized (infoMonitor) {
            infoMonitor.notifyAll();
        }

        if (notifyListeners) {
            String reason = disconnectReason != null ? disconnectReason : "Port closed";
            for (SerialListener listener : listeners) {
                listener.onDisconnected(reason);
            }
        }
    }

    private void stopReaderThread() {
        if (readerThread != null) {
            readerThread.shutdownNow();
            readerThread = null;
        }
    }
}
