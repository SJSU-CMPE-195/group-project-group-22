package edu.sjsu.spring2026.group32.hardware.serial;

import java.io.BufferedWriter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.function.Supplier;

public class SerialConnectionManager {
    private SerialDevice comPort;
    private Scanner scanner;
    private PrintWriter lineWriter;
    private static final int BAUD_RATE = 115200;

    private final Supplier<SerialDevice[]> portProvider;
    private final int readTimeoutMs;

    // ── Original constructor — keeps the default 100 ms used by the game loop ─
    public SerialConnectionManager(Supplier<SerialDevice[]> portProvider) {
        this(portProvider, 100);
    }

    // ── Extended constructor — callers choose their own read timeout ───────────
    public SerialConnectionManager(Supplier<SerialDevice[]> portProvider, int readTimeoutMs) {
        this.portProvider   = portProvider;
        this.readTimeoutMs  = readTimeoutMs;
    }

    // =========================================================================
    //  Auto-connect: scan all ports and pick the first compatible one.
    //  Used by HardwareSignalSource. Unchanged from original behaviour.
    // =========================================================================
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
                this.comPort.setComPortTimeouts(1, readTimeoutMs, 0);

                if (this.comPort.openPort()) {
                    System.out.println(">>> Serial Connection Established: " + port.getSystemPortName());
                    this.scanner = new Scanner(comPort.getInputStream());
                    initWriter();
                    return true;
                }
            }
        }
        System.err.println("CRITICAL: Compatible serial hardware not detected.");
        return false;
    }

    // =========================================================================
    //  Manual connect: caller supplies the exact device to open.
    //  Used by Test (GUI port selector). No auto-discovery, no Scanner —
    //  the caller reads via getInputStream() with its own BufferedReader.
    // =========================================================================
    public boolean connectTo(SerialDevice port) {
        this.comPort = port;
        this.comPort.setBaudRate(BAUD_RATE);
        // 1 = TIMEOUT_READ_SEMI_BLOCKING. Hardcoded to avoid jSerialComm import bleeding.
        this.comPort.setComPortTimeouts(1, readTimeoutMs, 0);

        if (this.comPort.openPort()) {
            System.out.println(">>> Serial Connection Established: " + port.getSystemPortName());
            // Initialize Scanner so HardwareSignalSource.getNextLine() works when the
            // Launcher passes this manager to a game's hardware stack.
            // bidirectionaltest.BidirectionalTest ignores the Scanner and reads via getInputStream() directly.
            this.scanner = new Scanner(comPort.getInputStream());
            initWriter();
            return true;
        }

        System.err.println(">>> Failed to open port: " + port.getSystemPortName());
        this.comPort = null;
        return false;
    }

    // =========================================================================
    //  Internal helpers
    // =========================================================================

    /**
     * Initialises the write-side PrintWriter from the open port's OutputStream.
     * Guarded against null so that test mocks that stub only getInputStream()
     * do not need to stub getOutputStream() to avoid a NullPointerException.
     */
    private void initWriter() {
        java.io.OutputStream out = comPort.getOutputStream();
        if (out != null) {
            this.lineWriter = new PrintWriter(
                    new BufferedWriter(new OutputStreamWriter(out)), true);
        }
    }

    // =========================================================================
    //  Read helpers
    // =========================================================================

    /**
     * Blocking line read via the internal Scanner.
     * Also checks {@link java.util.Scanner#ioException()} after a failed read —
     * on Windows this is how USB-removal surfaces, since the underlying
     * InputStream throws an IOException that Scanner stores rather than
     * re-throwing. When detected, the port is disconnected immediately so
     * callers (and the SerialConnectionPanel watchdog) see isConnected()==false.
     */
    public String getNextLine() {
        if (scanner == null) return null;
        try {
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                lastRxMs = System.currentTimeMillis(); // heartbeat — device is alive
                return line;
            }
            // hasNextLine() returned false — check whether the Scanner hit an
            // IOException internally (e.g. device physically removed on Windows).
            if (scanner.ioException() != null) {
                System.err.println(">>> Serial read error (device removed?): "
                        + scanner.ioException().getMessage());
                disconnect();
            }
            return null;
        } catch (Exception e) {
            System.err.println(">>> Serial read exception: " + e.getMessage());
            disconnect();
            return null;
        }
    }

    /**
     * Reads one line via the Scanner without updating the heartbeat timestamp.
     *
     * <p>Used internally by {@link #readInfoHandshake} so that the one-time
     * capability exchange at connect time does not make the watchdog believe a
     * real consumer is active.  If the handshake updated {@code lastRxMs}, the
     * 3-second timeout would start ticking immediately after connect, causing a
     * spurious "Device disconnected unexpectedly" when the Launcher is idle
     * (no game or test window open yet).</p>
     *
     * <p>All error handling is identical to {@link #getNextLine()}.</p>
     */
    private String readNextLineRaw() {
        if (scanner == null) return null;
        try {
            if (scanner.hasNextLine()) {
                return scanner.nextLine(); // no lastRxMs update
            }
            if (scanner.ioException() != null) {
                System.err.println(">>> Serial read error (device removed?): "
                        + scanner.ioException().getMessage());
                disconnect();
            }
            return null;
        } catch (Exception e) {
            System.err.println(">>> Serial read exception: " + e.getMessage());
            disconnect();
            return null;
        }
    }

    /**
     * Returns a readable InputStream view of the open port.
     * Intended for callers (e.g. Test) that manage their own BufferedReader
     * on a background thread. Returns null if not connected.
     *
     * <p>The returned stream is a non-owning view: closing it does not close
     * the underlying serial port. The {@link SerialConnectionManager} remains
     * the sole owner of the port lifecycle, so shared UI windows like
     * {@code BidirectionalTest} cannot accidentally disconnect Launcher-owned
     * hardware by closing their local reader wrappers.</p>
     */
    public InputStream getInputStream() {
        if (comPort == null) return null;

        InputStream in = comPort.getInputStream();
        if (in == null) return null;

        return new FilterInputStream(in) {
            @Override
            public void close() throws IOException {
                // The manager owns the port; external readers only borrow it.
            }
        };
    }

    // =========================================================================
    //  Write helper
    // =========================================================================

    /**
     * Sends a newline-terminated command to the device.
     * Safe to call from any thread; PrintWriter is thread-safe for single writes.
     */
    public void sendLine(String command) {
        if (lineWriter != null) {
            lineWriter.println(command);
            lineWriter.flush();
        }
    }

    // =========================================================================
    //  Stream heartbeat
    // =========================================================================

    /**
     * Timestamp (ms, wall clock) of the last line successfully returned by
     * {@link #getNextLine()} or acknowledged via {@link #refreshHeartbeat()}.
     * Left at 0 until a real consumer starts reading so the watchdog does not
     * start its timeout window while the Launcher is idle after connect.
     * {@code volatile} so the SerialConnectionPanel watchdog thread can read
     * it without synchronisation overhead.
     */
    private volatile long lastRxMs = 0;

    /**
     * How long (ms) without a received line before the connection is considered
     * lost.  The firmware sends a frame every 10 ms; 3 000 ms = 300 missed
     * frames, which is far beyond any normal OS scheduling jitter.
     */
    private static final long RX_TIMEOUT_MS = 3_000;

    /** @return wall-clock ms of the last received line, or 0 if never connected. */
    public long getLastRxMs() { return lastRxMs; }

    /** @return true if no line has been received within {@link #RX_TIMEOUT_MS}. */
    public boolean isRxTimedOut() {
        return lastRxMs > 0
            && (System.currentTimeMillis() - lastRxMs) > RX_TIMEOUT_MS;
    }

    /**
     * Updates the receive heartbeat timestamp to "now".
     *
     * <p>Callers that read from the port via {@link #getInputStream()} directly
     * (e.g. {@link edu.sjsu.spring2026.group32.bidirectionaltest.BidirectionalTest}) must call
     * this whenever they successfully receive a line, so that the
     * {@link edu.sjsu.spring2026.group32.launcher.SerialConnectionPanel} watchdog
     * does not mistake a healthy connection for a dead one.</p>
     *
     * <p>{@link #getNextLine()} calls this automatically, so callers that go
     * through the Scanner path do not need to call it explicitly.</p>
     */
    public void refreshHeartbeat() {
        lastRxMs = System.currentTimeMillis();
    }

    /**
     * Clears the receive heartbeat when an active consumer stops reading.
     *
     * <p>This returns the manager to the same idle state used immediately
     * after connect-time handshakes: the Launcher watchdog should not treat a
     * shared port as dead simply because no window is currently consuming the
     * stream. The next real read will call {@link #refreshHeartbeat()} or
     * {@link #getNextLine()} and start the timeout clock again.</p>
     */
    public void clearHeartbeat() {
        lastRxMs = 0;
    }

    // =========================================================================
    //  Device capability info (populated by readInfoHandshake)
    // =========================================================================

    /** Human-readable device name from the #INFO: handshake, e.g. "NeuralSignal". */
    private String deviceName         = "";

    /** Number of ADC channels reported by the firmware (0 = not yet queried). */
    private int    deviceChannelCount = 0;

    /** @return the device name from the last successful handshake, or "" if unknown. */
    public String getDeviceName()         { return deviceName; }

    /** @return ADC channel count from the last successful handshake, or 0 if unknown. */
    public int    getDeviceChannelCount() { return deviceChannelCount; }

    /**
     * Sends "INFO?" to the firmware and reads up to {@code maxAttempts} lines
     * looking for the "#INFO:" capability response.
     *
     * <p>The firmware responds within one loop tick (~10 ms) with a line like:
     * <pre>  #INFO:NeuralSignal,CH=2</pre>
     * Called by SerialConnectionPanel right after connectTo() succeeds.
     *
     * @param maxAttempts maximum lines to read while waiting for the response
     * @return true if a valid #INFO: line was received and parsed
     */
    public boolean readInfoHandshake(int maxAttempts) {
        if (!isConnected()) return false;
        sendLine("INFO?");
        for (int i = 0; i < maxAttempts; i++) {
            // Use readNextLineRaw() so the handshake does not seed lastRxMs.
            // See readNextLineRaw() javadoc for the full rationale.
            String line = readNextLineRaw();
            if (line != null && line.startsWith("#INFO:")) {
                parseInfo(line);
                return true;
            }
        }
        return false;
    }

    /**
     * Parses "#INFO:NeuralSignal,CH=2" into deviceName and deviceChannelCount.
     */
    private void parseInfo(String line) {
        String body = line.substring(6); // strip "#INFO:"
        for (String part : body.split(",")) {
            part = part.trim();
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if ("CH".equals(kv[0].trim())) {
                    try { deviceChannelCount = Integer.parseInt(kv[1].trim()); }
                    catch (NumberFormatException ignored) {}
                }
            } else if (!part.isEmpty()) {
                deviceName = part;
            }
        }
    }

    // =========================================================================
    //  State / lifecycle
    // =========================================================================

    public boolean isConnected() {
        return comPort != null && comPort.isOpen();
    }

    public void disconnect() {
        if (lineWriter != null) { lineWriter.close(); lineWriter = null; }
        if (scanner   != null) { scanner.close();    scanner   = null; }
        if (comPort   != null) { comPort.closePort(); comPort  = null; }
        deviceName         = "";
        deviceChannelCount = 0;
    }
}
