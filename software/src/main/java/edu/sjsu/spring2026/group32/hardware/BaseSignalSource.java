package edu.sjsu.spring2026.group32.hardware;

public interface BaseSignalSource {
    double getNextVoltage();

    /**
     * Returns {@code true} when the source has a discrete spike to report.
     *
     * <p>The default implementation is a <em>stateless</em> threshold check
     * ({@code getNextVoltage() >= threshold}), which is appropriate for test
     * stubs and software signal sources that produce clean, transient values.
     *
     * <p>{@link HardwareSignalSource} overrides this with the <em>stateful</em>
     * rising-edge detector in {@link NeuralSignalParser#hasSpike(double)}, so
     * that a single biological spike spanning multiple serial samples is counted
     * exactly once regardless of how many game ticks it covers.
     *
     * @param threshold voltage (V) at or above which the channel is "firing"
     * @return {@code true} if a spike is present this tick
     */
    default boolean hasSpike(double threshold) {
        return getNextVoltage() >= threshold;
    }
}
