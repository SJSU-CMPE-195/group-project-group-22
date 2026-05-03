package edu.sjsu.spring2026.group32.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight in-memory model of the ESP32 serial firmware protocol.
 */
public final class FakeSerialFirmware {
    private static final double V_MAX = 3.3;
    private static final int ADC_MAX = 4095;

    private final int channelCount;
    private final String deviceName;
    private final List<String> receivedCommands = new CopyOnWriteArrayList<>();

    private boolean[] injecting = new boolean[]{false, false};
    private int[] injectedRaw = new int[]{0, 0};

    public FakeSerialFirmware(int channelCount) {
        this(channelCount, "NeuralSignal");
    }

    public FakeSerialFirmware(int channelCount, String deviceName) {
        this.channelCount = Math.max(1, Math.min(2, channelCount));
        this.deviceName = deviceName;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public List<String> getReceivedCommands() {
        return List.copyOf(receivedCommands);
    }

    public String getLastReceivedCommand() {
        return receivedCommands.isEmpty() ? null : receivedCommands.get(receivedCommands.size() - 1);
    }

    public List<String> bootSequence() {
        return List.of(
                TestSerialLines.bootLine(channelCount),
                "#INFO:" + deviceName + ",CH=" + channelCount);
    }

    public String infoLine() {
        return "#INFO:" + deviceName + ",CH=" + channelCount;
    }

    public String sampleLine(long millis, int rawAdc1) {
        return TestSerialLines.sampleLineSingle(millis, rawAdc1);
    }

    public String sampleLine(long millis, int rawAdc1, int rawAdc2) {
        return TestSerialLines.sampleLineDual(millis, rawAdc1, rawAdc2);
    }

    public List<String> handleCommand(String command) {
        String trimmed = command == null ? "" : command.trim();
        receivedCommands.add(trimmed);

        if (trimmed.equals("INFO?")) {
            return List.of(infoLine());
        }
        if (trimmed.equals("STATUS")) {
            return List.of(statusLine());
        }
        if (trimmed.startsWith("INJECT_V_CH1:") || trimmed.startsWith("INJECT_V:")) {
            double volts = clampVolts(parseDoubleAfterColon(trimmed));
            injectedRaw[0] = voltsToRaw(volts);
            injecting[0] = true;
            return List.of(TestSerialLines.injectStartedLine(1, injectedRaw[0], volts));
        }
        if (trimmed.startsWith("INJECT_V_CH2:")) {
            if (channelCount < 2) {
                return List.of(TestSerialLines.errorLine("CH2_NOT_AVAILABLE"));
            }
            double volts = clampVolts(parseDoubleAfterColon(trimmed));
            injectedRaw[1] = voltsToRaw(volts);
            injecting[1] = true;
            return List.of(TestSerialLines.injectStartedLine(2, injectedRaw[1], volts));
        }
        if (trimmed.startsWith("INJECT_CH1:") || trimmed.startsWith("INJECT:")) {
            int raw = clampRaw(parseIntAfterColon(trimmed));
            injectedRaw[0] = raw;
            injecting[0] = true;
            return List.of(TestSerialLines.injectStartedLine(1, raw, rawToVolts(raw)));
        }
        if (trimmed.startsWith("INJECT_CH2:")) {
            if (channelCount < 2) {
                return List.of(TestSerialLines.errorLine("CH2_NOT_AVAILABLE"));
            }
            int raw = clampRaw(parseIntAfterColon(trimmed));
            injectedRaw[1] = raw;
            injecting[1] = true;
            return List.of(TestSerialLines.injectStartedLine(2, raw, rawToVolts(raw)));
        }
        if (trimmed.equals("STOP_INJECT")) {
            injecting = new boolean[]{false, false};
            injectedRaw = new int[]{0, 0};
            return List.of(TestSerialLines.injectStoppedLine("ALL"));
        }
        if (trimmed.equals("STOP_INJECT_CH1")) {
            injecting[0] = false;
            injectedRaw[0] = 0;
            return List.of(TestSerialLines.injectStoppedLine("CH1"));
        }
        if (trimmed.equals("STOP_INJECT_CH2")) {
            if (channelCount < 2) {
                return List.of(TestSerialLines.errorLine("CH2_NOT_AVAILABLE"));
            }
            injecting[1] = false;
            injectedRaw[1] = 0;
            return List.of(TestSerialLines.injectStoppedLine("CH2"));
        }

        return List.of(TestSerialLines.unknownCommandLine(trimmed));
    }

    public List<String> reconnectSequence() {
        List<String> lines = new ArrayList<>();
        lines.add(TestSerialLines.bootLine(channelCount));
        lines.add(infoLine());
        return lines;
    }

    public String statusLine() {
        if (channelCount >= 2) {
            return TestSerialLines.statusLine(
                    channelCount,
                    injecting[0],
                    injectedRaw[0],
                    rawToVolts(injectedRaw[0]),
                    injecting[1],
                    injectedRaw[1],
                    rawToVolts(injectedRaw[1]));
        }
        return TestSerialLines.statusLine(
                channelCount,
                injecting[0],
                injectedRaw[0],
                rawToVolts(injectedRaw[0]));
    }

    private static double clampVolts(double volts) {
        return Math.max(0.0, Math.min(V_MAX, volts));
    }

    private static int clampRaw(int raw) {
        return Math.max(0, Math.min(ADC_MAX, raw));
    }

    private static int voltsToRaw(double volts) {
        return clampRaw((int) ((volts / V_MAX) * ADC_MAX));
    }

    private static double rawToVolts(int raw) {
        return raw / (double) ADC_MAX * V_MAX;
    }

    private static double parseDoubleAfterColon(String command) {
        int index = command.indexOf(':');
        if (index < 0 || index == command.length() - 1) {
            return 0.0;
        }
        try {
            return Double.parseDouble(command.substring(index + 1));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static int parseIntAfterColon(String command) {
        int index = command.indexOf(':');
        if (index < 0 || index == command.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(command.substring(index + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
