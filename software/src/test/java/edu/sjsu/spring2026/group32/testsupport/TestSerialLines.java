package edu.sjsu.spring2026.group32.testsupport;

import java.util.Locale;

/**
 * Factory methods for the serial line formats emitted by the firmware.
 */
public final class TestSerialLines {
    private TestSerialLines() {
    }

    public static String bootLine(int channelCount) {
        return "STATUS,BOOT,NeuralSerial Ready,CH=" + channelCount;
    }

    public static String infoLine(int channelCount) {
        return "#INFO:NeuralSignal,CH=" + channelCount;
    }

    public static String sampleLineSingle(long millis, int rawAdc) {
        return millis + ",1,0," + rawAdc;
    }

    public static String sampleLineDual(long millis, int rawAdc1, int rawAdc2) {
        return millis + ",1,0," + rawAdc1 + "," + rawAdc2;
    }

    public static String injectStartedLine(int channel, int rawValue, double volts) {
        return String.format(Locale.US,
                "STATUS,INJECT_START,CH%d,%d,%.3fV",
                channel,
                rawValue,
                volts);
    }

    public static String injectStoppedLine(String scope) {
        return "STATUS,INJECT_STOPPED," + scope;
    }

    public static String errorLine(String code) {
        return "STATUS,ERR," + code;
    }

    public static String unknownCommandLine(String command) {
        return "STATUS,UNKNOWN_CMD," + command;
    }

    public static String statusLine(int channelCount,
                                    boolean ch1Injecting,
                                    int ch1Raw,
                                    double ch1Volts) {
        return statusLine(channelCount, ch1Injecting, ch1Raw, ch1Volts, false, 0, 0.0);
    }

    public static String statusLine(int channelCount,
                                    boolean ch1Injecting,
                                    int ch1Raw,
                                    double ch1Volts,
                                    boolean ch2Injecting,
                                    int ch2Raw,
                                    double ch2Volts) {
        StringBuilder builder = new StringBuilder();
        builder.append("STATUS,OK,CH=").append(channelCount)
                .append(",CH1=").append(ch1Injecting ? "INJECTING" : "NORMAL")
                .append(",").append(ch1Raw)
                .append(",").append(String.format(Locale.US, "%.3fV", ch1Volts));
        if (channelCount >= 2) {
            builder.append(",CH2=").append(ch2Injecting ? "INJECTING" : "NORMAL")
                    .append(",").append(ch2Raw)
                    .append(",").append(String.format(Locale.US, "%.3fV", ch2Volts));
        }
        return builder.toString();
    }
}
