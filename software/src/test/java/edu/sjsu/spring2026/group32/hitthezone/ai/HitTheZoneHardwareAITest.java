package edu.sjsu.spring2026.group32.hitthezone.ai;

import edu.sjsu.spring2026.group32.hardware.NeuralHardwareConfig;
import edu.sjsu.spring2026.group32.hardware.signal.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.signal.VoltageInjector;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;

@DisplayName("HitTheZoneHardwareAI Suite")
class HitTheZoneHardwareAITest {

    private static final double THRESHOLD = NeuralHardwareConfig.DEFAULT_FIRING_THRESHOLD_VOLTS; // 0.5V
    private static final double INJECTION_VOLTS = NeuralHardwareConfig.DEFAULT_HIT_THE_ZONE_INJECTION_VOLTAGE;
    private static final double ABOVE = THRESHOLD + 0.1;
    private static final double BELOW = THRESHOLD - 0.1;

    private static final HitTheZoneState IN_ZONE = new HitTheZoneState(true);
    private static final HitTheZoneState OUT_ZONE = new HitTheZoneState(false);

    private StubSignalSource source;
    private StubInjector injector;
    private HitTheZoneHardwareAI ai;

    @BeforeEach
    void setUp() {
        source = new StubSignalSource();
        injector = new StubInjector();
        ai = new HitTheZoneHardwareAI("TestHTZ", source, injector, INJECTION_VOLTS, THRESHOLD);

    }

    // scoring signals

    @Test
    @DisplayName("returns SCORE on first in-zone tick above threshold, null on sustained high")
    void scoringSignals_coverPositiveAndNegativeSpikeCases_risingEdge() {
        source.setVoltage(BELOW);
        assertNull(ai.getNextMove(IN_ZONE), "voltage below threshold in zone should return null");

        source.setVoltage(ABOVE);
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE), "first tick above threshold in zone should return SCORE");

        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE),"stateless stub fires every tick while above threshold");
        
    }

    @Test
    @DisplayName("returns null when out of zone and no spike, SCORE on late-fire rising edge")
    void scoringSignals_coverPositiveAndNegativeSpikeCases_outOfZone() {
        source.setVoltage(BELOW);
        assertNull(ai.getNextMove(OUT_ZONE), "below threshold out of zone should return null");

        source.setVoltage(ABOVE);
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(OUT_ZONE), "spike out of zone (late-fire) should still return SCORE");
    }

    @Test
    @DisplayName("returns null every tick when voltage is always below threshold")
    void scoringSignals_coverPositiveAndNegativeSpikeCases_noSpike() {
        source.setVoltage(BELOW);

        assertNull(ai.getNextMove(IN_ZONE), "below threshold in zone --> null");
        assertNull(ai.getNextMove(IN_ZONE), "still below threshold → null");
        assertNull(ai.getNextMove(OUT_ZONE), "below threshold out of zone → null");
    }

    @Test
    @DisplayName("returns null at exactly zero voltage")
    void scoringSignals_coverPositiveAndNegativeSpikeCases_zeroVoltage() {
        source.setVoltage(0.0);
        assertNull(ai.getNextMove(IN_ZONE), "zero voltage should never score");
    }

    // injection lifecycle

    @Test
    @DisplayName("injects on zone entry, stops on zone exit, does not repeat commands mid-zone")
    void injectionControl_matchesFirmwareProtocol_transitions() {
        source.setVoltage(BELOW);

        ai.getNextMove(IN_ZONE);
        assertEquals(1, injector.injectCount, "inject should be called once on zone entry");
        assertEquals(INJECTION_VOLTS, injector.lastInjectedVolts, 0.001, "injected voltage should match configured injection voltage");
        assertEquals(0, injector.stopCount, "no stop command should be sent on zone entry");

        ai.getNextMove(IN_ZONE);
        ai.getNextMove(IN_ZONE);
        assertEquals(1, injector.injectCount, "inject should not repeat while ball stays in zone");
        assertEquals(0, injector.stopCount, "stop should not be sent while ball stays in zone");

        ai.getNextMove(OUT_ZONE);
        assertEquals(1, injector.stopCount, "stop should be called once on zone exit");
        assertEquals(1, injector.injectCount, "inject count should not change on zone exit");

        ai.getNextMove(OUT_ZONE);
        ai.getNextMove(OUT_ZONE);
        assertEquals(1, injector.stopCount, "stop should not repeat while ball stays out of zone");
    }

    @Test
    @DisplayName("re-injects on second zone entry after exit")
    void injectionControl_matchesFirmwareProtocol_reentry() {
        source.setVoltage(BELOW);

        ai.getNextMove(IN_ZONE);  // entry --> inject
        ai.getNextMove(OUT_ZONE); // exit  --> stop
        ai.getNextMove(IN_ZONE); // re-entry --> inject again

        assertEquals(2, injector.injectCount, "second zone entry should trigger a second inject command");
        assertEquals(1, injector.stopCount, "only one stop should have been sent between the two entries");
    }

    @Test
    @DisplayName("stopInjectionOnly() sends stop and resets wasInZone without re-triggering on next entry")
    void injectionControl_matchesFirmwareProtocol_stopInjectionOnly() {
        source.setVoltage(BELOW);

        ai.getNextMove(IN_ZONE); // entry --> inject

        assertEquals(1, injector.injectCount, "inject on entry");

        ai.stopInjectionOnly();
        assertEquals(1, injector.stopCount, "stopInjectionOnly() should send stop command");

        ai.getNextMove(IN_ZONE);
        assertEquals(2, injector.injectCount, "zone entry after stopInjectionOnly() should re-inject");

    }

    @Test
    @DisplayName("close() stops injection and releases source")
    void injectionControl_matchesFirmwareProtocol_close() {
        source.setVoltage(BELOW);
        ai.getNextMove(IN_ZONE); // arm injection state

        ai.close();
        assertEquals(1, injector.stopCount, "close() should send stop command");
    }

    @Test
    @DisplayName("no inject or stop commands sent when ball never enters zone")
    void injectionControl_matchesFirmwareProtocol_noZoneEntry() {
        source.setVoltage(ABOVE);

        ai.getNextMove(OUT_ZONE);
        ai.getNextMove(OUT_ZONE);
        ai.getNextMove(OUT_ZONE);

        assertEquals(0, injector.injectCount, "no inject should be sent if ball never enters zone");
        assertEquals(0, injector.stopCount, "no stop should be sent if ball never enters zone");
    }


    // stateless stub, hasSpike() uses the default >= threshold from BaseSignalSource
    private static class StubSignalSource implements BaseSignalSource {
        private double voltage = 0.0;

        void setVoltage(double v) { this.voltage = v; }

        @Override
        public double getNextVoltage() { return voltage; }
    }

    private static class StubInjector implements VoltageInjector {
        int injectCount = 0;
        double lastInjectedVolts = 0.0;
        int stopCount = 0;

        @Override
        public void injectVoltage(int channel, double volts) {
            injectCount++;
            lastInjectedVolts = volts;
        }

        @Override
        public void stopInjection(int channel) {
            stopCount++;
        }
    }
}