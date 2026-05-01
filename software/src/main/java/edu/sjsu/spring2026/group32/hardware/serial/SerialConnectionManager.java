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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class SerialConnectionManager {
    public record SampleFrame(String line,
                              long millis,
                              int primaryRaw,
                              Integer secondaryRaw) {}

    public interface SerialListener {
        default void onConnected(String portName) {}
        default void onSample(SampleFrame frame) {}
        default void onSampleLine(String line) {}
        default void onStatusPayload(String payload) {}
        default void onInfoUpdated(String deviceName, int channelCount) {}
        default void onDisconnected(String reason) {}
    }

    private static final int BAUD_RATE = 115200;
    private static final long RX_TIMEOUT_MS = 3_000;
    private static final long INFO_WAIT_SLICE_MS = 25;

    /** Canonical set of USB-UART bridge keywords that identify ESP32 hardware. */
    private static final String[] KNOWN_ESP32_BRIDGE_KEYWORDS = {
        "CP210", "CH340", "CH341", "FT232", "FTDI", "ESP32", "ESP8266",
        "Silicon Laboratories", "Silicon Labs"
    };

    private final Supplier<SerialDevice[]> portProvider;
    private final int readTimeoutMs;
    private final List<SerialListener> listeners = new CopyOnWriteArrayList<>();
    private final Object infoMonitor = new Object();

    private SerialDevice comPort;
    private PrintWriter lineWriter;
    private ExecutorService readerThread;
    private ScheduledExecutorService watchdogThread;
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

    /**
     * Returns {@code true} if the given device's descriptive name matches a
     * known ESP32 USB-UART bridge adapter (CP210x, CH340/341, FT232/FTDI,
     * native ESP32/ESP8266 USB, or Silicon Labs).
     *
     * <p>This is the single authoritative keyword list used by both the
     * auto-connect path and the UI filter in {@code SerialConnectionPanel}.</p>
     */
    public static boolean isKnownEsp32Bridge(SerialDevice device) {
        String name = device.getDescriptivePortName().toUpperCase();
        for (String keyword : KNOWN_ESP32_BRIDGE_KEYWORDS) {
            if (name.contains(keyword.toUpperCase())) return true;
        }
        return false;
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
            if (isKnownEsp32Bridge(port)) {
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
        for (SerialListener listener : listeners) {
            listener.onConnected(port.getSystemPortName());
        }
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
        startWatchdog();
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
        if (comPort == null) {
            // Already disconnected; wake any thread blocked in readInfoHandshake and return.
            synchronized (infoMonitor) { infoMonitor.notifyAll(); }
            return;
        }

        if (gracefulStop && isConnected()) {
            stopAllInjection();
        }

        readerRunning = false;
        stopReaderThread();
        stopWatchdog();

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

    /**
     * Starts a 1-second polling loop that fires {@code onDisconnected} if no
     * data has been received for {@link #RX_TIMEOUT_MS} milliseconds.
     *
     * <p>Using {@code shutdown()} rather than {@code shutdownNow()} avoids
     * self-interruption when the watchdog task itself calls
     * {@link #disconnectInternal}.</p>
     */
    private void startWatchdog() {
        stopWatchdog();
        ScheduledExecutorService wdt = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SerialConnectionManager-Watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdogThread = wdt;
        wdt.scheduleAtFixedRate(() -> {
            if (isRxTimedOut()) {
                disconnectInternal(false, "RX timeout — no data for 3 s", true);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopWatchdog() {
        ScheduledExecutorService wdt = watchdogThread;
        watchdogThread = null;
        if (wdt != null) {
            wdt.shutdown();   // don't interrupt — may be called from the watchdog thread itself
        }
    }
}
