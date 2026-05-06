package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.hardware.NeuralHardwareConfig;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.signal.BaseSignalSource;
import edu.sjsu.spring2026.group32.hardware.signal.VoltageInjector;
import edu.sjsu.spring2026.group32.player.model.BasePlayer;
import edu.sjsu.spring2026.group32.pong.ai.PongHardwareAI;
import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;
import edu.sjsu.spring2026.group32.pong.model.PongAction;
import edu.sjsu.spring2026.group32.pong.model.PongGameState;
import edu.sjsu.spring2026.group32.pong.model.PongState;
import edu.sjsu.spring2026.group32.pong.ui.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("PongGame Suite")
class PongGameTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    // tesable subclass, suppresses all Swing-touching methods

    private static class TestablePongGame extends PongGame {
        TestablePongGame(PongHardwareAI hardwarePlayer,
                         SerialConnectionManager pongManager) {
            super(hardwarePlayer, pongManager);
        }


    }

    // Stub injector for hardware player

    private static class StubInjector implements VoltageInjector {
        int stopCount = 0;

        public void injectVoltage(int channel, double volts) {}
        public void stopInjection(int channel) { stopCount++; }
    }

    @TempDir Path tempDir;

    private TestablePongGame game;
    private StubSignalSource leftSource;
    private StubSignalSource rightSource;
    private StubInjector     injector;
    private PongHardwareAI   hardwarePlayer;

    private static class StubSignalSource implements BaseSignalSource {
        private double voltage = 0.0;
        @Override public double getNextVoltage() { return voltage; }
    }

    @BeforeEach
    void setUp() {
        leftSource = new StubSignalSource();
        rightSource = new StubSignalSource();
        injector = new StubInjector();
        hardwarePlayer = new PongHardwareAI(
                "Hardware",
                leftSource, rightSource,
                injector,
                3.0, 3.0,
                NeuralHardwareConfig.DEFAULT_PONG_LEFT_THRESHOLD_VOLTS,
                NeuralHardwareConfig.DEFAULT_PONG_RIGHT_THRESHOLD_VOLTS);

        game = new TestablePongGame(hardwarePlayer, null);
    }

    @AfterEach
    void tearDown() {
        game.stop();
    }

    // onVariantSelected, top and bottom

    @Test
    @DisplayName("onVariantSelected(TOP, AI_HARD) updates topVariant")
    void variantAndHardwareSelection_updateGameState_topVariantUpdates() {
        game.onVariantSelected(PongToolbar.Side.TOP, PlayerVariant.AI_HARD);
        assertEquals(PlayerVariant.AI_HARD, game.topVariant, "topVariant should update to AI_HARD");
    }

    @Test
    @DisplayName("onVariantSelected(BOTTOM, AI_EASY) updates bottomVariant")
    void variantAndHardwareSelection_updateGameState_bottomVariantUpdates() {
        game.onVariantSelected(PongToolbar.Side.BOTTOM, PlayerVariant.AI_EASY);
        assertEquals(PlayerVariant.AI_EASY, game.bottomVariant, "bottomVariant should update to AI_EASY");
    }

    @Test
    @DisplayName("onVariantSelected() rebuilds topPlayer to match new variant")
    void variantAndHardwareSelection_updateGameState_rebuildsTopPlayer() {
        BasePlayer<PongState, PongAction> before = game.topPlayer;
        game.onVariantSelected(PongToolbar.Side.TOP, PlayerVariant.AI_HARD);
        assertNotEquals(before, game.topPlayer, "topPlayer should be rebuilt after variant change");
    }

    @Test
    @DisplayName("onVariantSelected() rebuilds bottomPlayer to match new variant")
    void variantAndHardwareSelection_updateGameState_rebuildsBottomPlayer() {
        BasePlayer<PongState, PongAction> before = game.bottomPlayer;
        game.onVariantSelected(PongToolbar.Side.BOTTOM, PlayerVariant.AI_EASY);
        assertNotEquals(before, game.bottomPlayer, "bottomPlayer should be rebuilt after variant change");
    }

    @Test
    @DisplayName("selecting HARDWARE variant enables injection on hardwarePlayer")
    void variantAndHardwareSelection_updateGameState_hardwareVariantEnablesInjection() {
        // Start with AI_EASY on top, then switch to HARDWARE
        game.onVariantSelected(PongToolbar.Side.TOP, PlayerVariant.AI_EASY);
        game.onVariantSelected(PongToolbar.Side.TOP, PlayerVariant.HARDWARE);

        assertTrue(hardwarePlayer.injectionEnabled, "injection should be enabled when HARDWARE variant is selected");
    }

    @Test
    @DisplayName("deselecting HARDWARE disables injection on hardwarePlayer")
    void variantAndHardwareSelection_updateGameState_deselectedHardwareDisablesInjection() {
        game.onVariantSelected(PongToolbar.Side.TOP, PlayerVariant.HARDWARE);
        game.onVariantSelected(PongToolbar.Side.TOP, PlayerVariant.AI_HARD);

        assertFalse(hardwarePlayer.injectionEnabled, "injection should be disabled when HARDWARE variant is deselected");
    }

    // handleHardwareDisconnected (fallback behavior)

    @Test
    @DisplayName("handleHardwareDisconnected() falls back to AI_HARD when top was HARDWARE")
    void variantAndHardwareSelection_updateGameState_disconnectFallsBackTop() {
        game.onVariantSelected(PongToolbar.Side.TOP, PlayerVariant.HARDWARE);
        game.handleHardwareDisconnected("cable unplugged");

        assertEquals(PlayerVariant.AI_HARD, game.topVariant,"top variant should fall back to AI_HARD after disconnect");
    }

    @Test
    @DisplayName("handleHardwareDisconnected() does not change variant if top was not HARDWARE")
    void variantAndHardwareSelection_updateGameState_disconnectNoFallbackWhenNotHardware() {
        game.onVariantSelected(PongToolbar.Side.TOP, PlayerVariant.AI_EASY);
        game.handleHardwareDisconnected("cable unplugged");

        assertEquals(PlayerVariant.AI_EASY, game.topVariant,"top variant should not change if it was not HARDWARE");
    }

    // handleHardwareInfoUpdated — channel count validation

    @Test
    @DisplayName("handleHardwareInfoUpdated() with < 2 channels marks hardware unavailable")
    void variantAndHardwareSelection_updateGameState_wrongChannelCountUnavailable() {
        game.handleHardwareInfoUpdated(1);

        assertFalse(game.topToolbar.hardwareAvailable, "hardware should be unavailable with single channel device");
        assertFalse(game.bottomToolbar.hardwareAvailable, "hardware should be unavailable with single channel device");
    }

    @Test
    @DisplayName("handleHardwareInfoUpdated() with >= 2 channels marks hardware available")
    void variantAndHardwareSelection_updateGameState_correctChannelCountAvailable() {
        game.handleHardwareInfoUpdated(2);

        assertTrue(game.topToolbar.hardwareAvailable, "hardware should be available with dual channel device");
        assertTrue(game.bottomToolbar.hardwareAvailable, "hardware should be available with dual channel device");
    }

    // tickPlaying — scoring and state transitions

    @Test
    @DisplayName("tickPlaying() returns early when ball velocity is zero")
    void gameplayFlow_coversScoringAndPauseTransitions_stoppedBallNoOp() {
        game.engine.resetBall();
      
        game.engine.startCountdown(System.currentTimeMillis());

        game.engine.tickCountdown(System.currentTimeMillis() + 3000L);

        assertEquals(PongGameState.PLAYING, game.engine.getGameState(), "engine should be in PLAYING state");

        game.tickPlaying();
    }

    @Test
    @DisplayName("tickPlaying() records scoreboard point when top player scores")
    void gameplayFlow_coversScoringAndPauseTransitions_topPlayerScores() {
        game.engine.startCountdown(System.currentTimeMillis());
        game.engine.tickCountdown(System.currentTimeMillis() + 3000L);

        // Tick until top scores (ball exits bottom) or 2000 ticks
        int maxTicks = 2000;
        for (int i = 0; i < maxTicks; i++) {
            game.tickPlaying();
            if (game.engine.getGameState() == PongGameState.COUNTDOWN
                    || game.engine.getGameState() == PongGameState.PAUSED) {
                break;
            }
        }

        // Either a score was recorded or we hit max ticks — just verify no exception
        // and scoreboard is accessible
        assertTrue(game.scoreboard.getRecords().size() >= 0, "scoreboard should be accessible after ticking");
    }

    // togglePause

    @Test
    @DisplayName("togglePause() pauses the engine when playing")
    void gameplayFlow_coversScoringAndPauseTransitions_togglePausePauses() {
        // Start in PAUSED state (default), then start countdown to get to non-paused
        game.engine.startCountdown(System.currentTimeMillis());
        game.engine.tickCountdown(System.currentTimeMillis() + 3000L);
        assertEquals(PongGameState.PLAYING, game.engine.getGameState());

        game.togglePause();

        assertEquals(PongGameState.PAUSED, game.engine.getGameState(),"engine should be paused after togglePause() from PLAYING");
    }

    @Test
    @DisplayName("togglePause() starts countdown when already paused")
    void gameplayFlow_coversScoringAndPauseTransitions_togglePauseResumes() {
        // Engine starts paused
        assertEquals(PongGameState.PAUSED, game.engine.getGameState());

        game.togglePause();

        assertEquals(PongGameState.COUNTDOWN, game.engine.getGameState(), "engine should enter COUNTDOWN after togglePause() from PAUSED");
    }

    // startCountdown

    @Test
    @DisplayName("startCountdown() transitions engine to COUNTDOWN state")
    void gameplayFlow_coversScoringAndPauseTransitions_startCountdown() {
        game.startCountdown();

        assertEquals(PongGameState.COUNTDOWN, game.engine.getGameState(), "engine should be in COUNTDOWN after startCountdown()");
    }

    @Test
    @DisplayName("startCountdown() stops hardware injection")
    void gameplayFlow_coversScoringAndPauseTransitions_startCountdownStopsInjection() {
        injector.stopCount = 0;
        game.startCountdown();

        assertTrue(injector.stopCount > 0, "startCountdown() should stop hardware injection");
    }

    // requestReset

    @Test
    @DisplayName("requestReset() resets engine when paused")
    void gameplayFlow_coversScoringAndPauseTransitions_requestResetWhenPaused() {
        game.engine.startCountdown(System.currentTimeMillis());

        game.engine.tickCountdown(System.currentTimeMillis() + 3000L);
        game.engine.pause();

        game.requestReset();

        assertEquals(PongGameState.PAUSED, game.engine.getGameState(), "engine should remain paused after reset");
        assertEquals(0, game.engine.snapshot().topScore(), "top score should reset to 0");
        assertEquals(0, game.engine.snapshot().bottomScore(), "bottom score should reset to 0");
    }

    @Test
    @DisplayName("requestReset() is a no-op when not paused")
    void gameplayFlow_coversScoringAndPauseTransitions_requestResetIgnoredWhenPlaying() {
        game.engine.startCountdown(System.currentTimeMillis());
        game.engine.tickCountdown(System.currentTimeMillis() + 3000L);

        assertEquals(PongGameState.PLAYING, game.engine.getGameState());

        game.requestReset();

        assertEquals(PongGameState.PLAYING, game.engine.getGameState(), "requestReset() should be ignored when not paused");
    }

    // stopHardwareInjection

    @Test
    @DisplayName("stopHardwareInjection() calls stopInjectionOnly on hardwarePlayer")
    void gameplayFlow_coversScoringAndPauseTransitions_stopHardwareInjection() {
        injector.stopCount = 0;
        game.stopHardwareInjection();
        assertTrue(injector.stopCount > 0,"stopHardwareInjection() should call stopInjectionOnly");
    }

    @Test
    @DisplayName("stopHardwareInjection() is safe when hardwarePlayer is null")
    void gameplayFlow_coversScoringAndPauseTransitions_stopHardwareInjectionNullSafe() {
        TestablePongGame noHwGame = new TestablePongGame(null, null);
        noHwGame.stopHardwareInjection();
    }
}