package edu.sjsu.spring2026.group32.pong.ai;

import edu.sjsu.spring2026.group32.hardware.NeuralHardwareConfig;
import edu.sjsu.spring2026.group32.hardware.signal.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.signal.VoltageInjector;
import edu.sjsu.spring2026.group32.pong.core.PongEngine;
import edu.sjsu.spring2026.group32.pong.model.PongAction;
import edu.sjsu.spring2026.group32.pong.model.PongState;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PongHardwareAI Suite")
class PongHardwareAITest {

    private static final double THRESHOLD = NeuralHardwareConfig.DEFAULT_FIRING_THRESHOLD_VOLTS; // 0.5V
    private static final double ABOVE = THRESHOLD + 0.1;
    private static final double BELOW = THRESHOLD - 0.1;
    private static final int FIELD_WIDTH = PongEngine.FIELD_WIDTH;

    // paddleX=300 --> paddleCenter = 300 + 40 = 340
    private static final int PADDLE_X = 300;
    private static final int PADDLE_CENTER = PADDLE_X + PongEngine.PADDLE_WIDTH / 2; // 340

    private StubSignalSource leftSource;
    private StubSignalSource rightSource;
    private StubInjector injector;
    private PongHardwareAI ai;

    @BeforeEach
    void setUp() {
        leftSource = new StubSignalSource();
        rightSource = new StubSignalSource();
        injector = new StubInjector();
        ai = new PongHardwareAI(
            "Test_Hardware",
            leftSource,
            rightSource,
            injector,
            3.0, // leftInjectionVoltage
            3.0, // rightInjectionVoltage
            THRESHOLD,
            THRESHOLD);
    }
    // spike-driven movement

    @Test
    @DisplayName("getNextMove() returns LEFT on left spike, RIGHT on right spike, IDLE when neither fires")
    void spikeDrivenMovement_usesDualChannelFirmwareSignals() {
        // simulates ball at paddle center
        PongState state = ballIncoming(PADDLE_CENTER, PADDLE_X);

        // simulates neither channel firing — IDLE
        leftSource.setVoltage(BELOW);
        rightSource.setVoltage(BELOW);
        assertEquals(PongAction.IDLE, ai.getNextMove(state), "no spike on either channel should produce IDLE");

        // simulates left channel fires — LEFT
        leftSource.setVoltage(ABOVE);
        rightSource.setVoltage(BELOW);
        assertEquals(PongAction.LEFT, ai.getNextMove(state), "spike on left channel should produce LEFT");

        // simulates right channel fires — RIGHT
        leftSource.setVoltage(BELOW);
        rightSource.setVoltage(ABOVE);
        assertEquals(PongAction.RIGHT, ai.getNextMove(state), "spike on right channel should produce RIGHT");

        // simulates both fire simultaneously — LEFT takes precedence (per class contract)
        leftSource.setVoltage(ABOVE);
        rightSource.setVoltage(ABOVE);
        assertEquals(PongAction.LEFT, ai.getNextMove(state), "simultaneous spikes should prefer LEFT");

        // simulates exactly at threshold counts as firing (>=)
        leftSource.setVoltage(BELOW);
        rightSource.setVoltage(THRESHOLD);
        assertEquals(PongAction.RIGHT, ai.getNextMove(state), "voltage exactly at threshold should be treated as firing");

    }

    @Test
    @DisplayName("getNextMove() accumulates spike counts correctly per channel")
    void spikeDrivenMovement_countsSpikesPerChannel() {
        PongState state = ballIncoming(PADDLE_CENTER, PADDLE_X);

        leftSource.setVoltage(ABOVE);
        rightSource.setVoltage(BELOW);

        ai.getNextMove(state); // left fires

        leftSource.setVoltage(BELOW);
        rightSource.setVoltage(ABOVE);

        ai.getNextMove(state); // right fires

        leftSource.setVoltage(ABOVE);
        rightSource.setVoltage(BELOW);

        ai.getNextMove(state); // left fires again

        assertEquals(3, ai.getSpikeCount(), "total spike count should be 3");
        assertEquals(2, ai.getCh1SpikeCount(), "ch1 should have fired twice");
        assertEquals(1, ai.getCh2SpikeCount(), "ch2 should have fired once");

        ai.resetSpikeCount();

        assertEquals(0, ai.getSpikeCount(), "total spike count should reset to 0");
        assertEquals(0, ai.getCh1SpikeCount(), "ch1 spike count should reset to 0");
        assertEquals(0, ai.getCh2SpikeCount(), "ch2 spike count should reset to 0");

    }

    // injection enable / disable and lifecycle

    @Test
    @DisplayName("injection commands are sent only when enabled, and stop when disabled")
    void injectionAndReconnect_coverLifecyclePaths() {
        // paddleCenter=340; ballX=100; diff=100-340=-240 < -DEAD_ZONE(-20)
        PongState ballFarLeft = ballIncoming(100, PADDLE_X);
        // paddleCenter=340; ballX=500; diff=500-340=160 > DEAD_ZONE(20)
        PongState ballFarRight = ballIncoming(500, PADDLE_X);
        // paddleCenter=340; ballX=340; diff=0, within dead zone
        PongState ballCenter = ballIncoming(PADDLE_CENTER, PADDLE_X);

        ai.getNextMove(ballFarLeft);
        assertTrue(injector.injectCalls.isEmpty(), "no inject commands should be sent while injection is disabled");

        ai.setInjectionEnabled(true);
        ai.getNextMove(ballFarLeft);
        assertFalse(injector.injectCalls.isEmpty(), "inject command should be sent after injection is enabled");
        assertEquals(1, injector.injectCalls.get(0).channel, "CH1 should be injected when ball is to the left");
        assertEquals(3.0, injector.injectCalls.get(0).volts, 0.001, "injection voltage should match configured left voltage");

        injector.clear();
        ai.getNextMove(ballFarRight);
        assertTrue(injector.stopCalls.contains(1), "CH1 should be stopped before switching to CH2");
        assertTrue(injector.injectCalls.stream().anyMatch(c -> c.channel == 2), "CH2 should be injected when ball is to the right");

        injector.clear();
        ai.getNextMove(ballFarLeft); // sets lastInject = LEFT

        injector.clear();
        ai.getNextMove(ballCenter); // diff=0 → dead zone → stop all

        assertTrue(injector.stopCalls.contains(0), "global stop should be sent when ball is within dead zone");
        assertTrue(injector.injectCalls.isEmpty(), "no inject should be sent when ball is within dead zone");

        injector.clear();
        ai.getNextMove(ballFarLeft); // sets lastInject = LEFT

        injector.clear();

        PongState ballAway = ballMovingAway(100, PADDLE_X);
        ai.getNextMove(ballAway);
        assertTrue(injector.stopCalls.contains(0), "global stop should fire when ball changes to moving away");
        injector.clear();
        ai.getNextMove(ballAway); // repeated away ticks should not re-send stop
        assertTrue(injector.stopCalls.isEmpty(), "stop should not be re-sent if ball is still moving away");

        // disable injection 
        injector.clear();
        ai.setInjectionEnabled(false);
        assertTrue(injector.stopCalls.contains(0), "disabling injection should immediately send global stop");

        // after disable, no inject commands on subsequent ticks
        injector.clear();
        ai.getNextMove(ballFarLeft);
        assertTrue(injector.injectCalls.isEmpty(), "no inject commands should be sent after injection is disabled");
    }

    // helpers

    /** Ball incoming toward top player (ballVelY < 0). */
    private static PongState ballIncoming(int ballX, int paddleX) {
        return new PongState(paddleX, ballX, 100, FIELD_WIDTH, -3);
    }

    /** Ball moving away from top player (ballVelY >= 0). */
    private static PongState ballMovingAway(int ballX, int paddleX) {
        return new PongState(paddleX, ballX, 100, FIELD_WIDTH, 3);
    }

    // stubs

    /** Stateless stub — hasSpike() uses the default >= threshold from BaseSignalSource. */
    private static class StubSignalSource implements BaseSignalSource {
        private double voltage = 0.0;

        void setVoltage(double v) { this.voltage = v; }

        @Override
        public double getNextVoltage() { return voltage; }
    }

    private static class InjectionCall {
        final int    channel;
        final double volts;

        InjectionCall(int channel, double volts) {
            this.channel = channel;
            this.volts   = volts;
        }
    }

    private static class StubInjector implements VoltageInjector {
        final List<InjectionCall> injectCalls = new ArrayList<>();
        final List<Integer>       stopCalls   = new ArrayList<>();

        @Override
        public void injectVoltage(int channel, double volts) {
            injectCalls.add(new InjectionCall(channel, volts));
        }

        @Override
        public void stopInjection(int channel) {
            stopCalls.add(channel);
        }

        void clear() {
            injectCalls.clear();
            stopCalls.clear();
        }
    }
}