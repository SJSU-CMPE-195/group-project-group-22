package edu.sjsu.spring2026.group32.hardware.serial;

import java.io.BufferedWriter;
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
            // BidirectionalTest ignores the Scanner and reads via getInputStream() directly.
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

    /** Blocking line read via the internal Scanner (auto-connect path only). */
    public String getNextLine() {
        if (scanner != null && scanner.hasNextLine()) {
            return scanner.nextLine();
        }
        return null; // Return null if no data is ready, letting the parser handle it
    }

    /**
     * Returns the raw InputStream of the open port.
     * Intended for callers (e.g. Test) that manage their own BufferedReader
     * on a background thread. Returns null if not connected.
     */
    public InputStream getInputStream() {
        return comPort != null ? comPort.getInputStream() : null;
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
    //  State / lifecycle
    // =========================================================================

    public boolean isConnected() {
        return comPort != null && comPort.isOpen();
    }

    public void disconnect() {
        if (lineWriter != null) { lineWriter.close(); lineWriter = null; }
        if (scanner   != null) { scanner.close();    scanner   = null; }
        if (comPort != null && comPort.isOpen()) {
            comPort.closePort();
            System.out.println(">>> Serial Connection Closed.");
        }
        comPort = null;
    }
}
