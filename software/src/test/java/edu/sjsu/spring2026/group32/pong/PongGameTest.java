package edu.sjsu.spring2026.group32.pong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.*;

import edu.sjsu.spring2026.group32.pong.ui.PongToolbar;

/**
 * Unit / integration tests for {@link PongGame}.
 *
 * <p><b>NOTE:</b> {@code PongGame} extends {@code JPanel} and relies on Swing
 * rendering, {@code javax.swing.Timer}, and AWT event dispatch.  Tests require
 * either a real display or a virtual one ({@code xvfb-run} on CI).
 *
 */
@DisplayName("PongGame Suite")
class PongGameTest {

    private PongGame game;

    @BeforeEach
    void setUp() throws Exception {
        // constructs PongGame with two software AI players to avoid hardware dependencies 
        SwingUtilities.invokeAndWait(() -> game = new PongGame(null));
    }

    @AfterEach
    void tearDown() {
        if(game != null) game.stop();
    }


    // ──────────────────────────────────────────────────────────────────────────
    // State machine
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Initial game state is PAUSED")
    void initialStateIsPaused() {
        assertEquals("PAUSED", game.gameState.name(), "Game should start in PAUSED state");
    }

    @Test
    @DisplayName("Starting the game transitions state to COUNTDOWN")
    void startTransitionsToCountdown() throws Exception {
        SwingUtilities.invokeAndWait(() -> game.togglePause());
        assertEquals("COUNTDOWN", game.gameState.name(), "Toggling pause from PAUSED should transition to COUNTDOWN");
    }

    @Test
    @DisplayName("After countdown completes, state transitions to PLAYING")
    void countdownTransitionsToPlaying() throws Exception {
        
        SwingUtilities.invokeAndWait(() -> game.startCountdown());

        // sets countdownStartMs back 2 seconds 
        game.countdownStartMs = System.currentTimeMillis() - 2500;

        game.tickCountdown(System.currentTimeMillis());

        assertEquals("PLAYING", game.gameState.name(), "State should transition to PLAYING after countdown expires");


    }

    // ──────────────────────────────────────────────────────────────────────────
    // Ball physics
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ball exits top boundary — scores for bottom player, no Y wall bounce exists")
    void ballReversesOnTopWall() throws Exception {
        SwingUtilities.invokeAndWait(() -> game.startCountdown());
        game.gameState = PongGame.GameState.PLAYING;

        int before = game.bottomScore;

        // simulates ball exiting above the top boundary
        game.ballY = -PongGame.BALL_SIZE - 1;
        game.ballVelY = -5;
        game.tickPlaying();

        assertEquals(before + 1, game.bottomScore, "Bottom player should score when ball exits top");

    }

    @Test
    @DisplayName("Ball exits bottom boundary — scores for top player")
    void ballReversesOnBottomWall() throws Exception {
        SwingUtilities.invokeAndWait(() -> game.startCountdown());
        game.gameState = PongGame.GameState.PLAYING;

        int before = game.topScore;

        // simulates ball exiting below the bottom boundary
        game.ballY = PongGame.FIELD_HEIGHT + 1;
        game.ballVelY = 5;
        game.tickPlaying();

        assertEquals(before + 1, game.topScore, "Top player should score when ball exits bottom");
    }

    @Test
    @DisplayName("Ball reverses horizontal direction on left wall collision")
    void ballReversesOnLeftWall() throws Exception {
        SwingUtilities.invokeAndWait(() -> game.startCountdown());
        game.gameState = PongGame.GameState.PLAYING;

        // simulates ball at left edge moving left
        game.ballX = 0;
        game.ballVelX = -5;
        game.ballVelY = 0;
        game.tickPlaying();

        assertTrue(game.ballVelX > 0, "Ball should reverse rightward after hitting left wall");
    }

    @Test
    @DisplayName("Ball reverses horizontal direction on right wall collision")
    void ballReversesOnRightWall() throws Exception {
        SwingUtilities.invokeAndWait(() -> game.startCountdown());
        game.gameState = PongGame.GameState.PLAYING;

        // simulates ball at right edge moving right
        game.ballX = PongGame.FIELD_WIDTH - PongGame.BALL_SIZE;
        game.ballVelX = 5;
        game.ballVelY = 0;
        game.tickPlaying();

        assertTrue(game.ballVelX < 0, "Ball should reverse leftward after hitting right wall");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scoring
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ball passing top paddle awards a point to bottom player")
    void ballPassingTopPaddleScoresForBottom() throws Exception {
        SwingUtilities.invokeAndWait( () -> game.startCountdown());
        game.gameState = PongGame.GameState.PLAYING;

        int before = game.bottomScore;

        // simulates ball exiting above the top boundary (missed by top paddle)
        game.ballY = -PongGame.BALL_SIZE - 1;
        game.ballVelY = -5; 
        game.tickPlaying();

        assertEquals(before + 1, game.bottomScore, "Bottom player should score when ball passes top paddle");
        
    }

    @Test
    @DisplayName("Ball passing bottom paddle awards a point to top player")
    void ballPassingBottomPaddleScoresForTop() throws Exception {
        SwingUtilities.invokeAndWait( () -> game.startCountdown());
        game.gameState = PongGame.GameState.PLAYING;

        int before = game.topScore; 

        // simulates ball exiting below bottom boundary (missed by bottom paddle)
        game.ballY = PongGame.FIELD_HEIGHT + 1;
        game.ballVelY = 5; 
        game.tickPlaying();

        assertEquals(before + 1, game.topScore, "Top player should score when ball passes bottom paddle");

    }

    // ──────────────────────────────────────────────────────────────────────────
    // Player variant switching
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setPlayerVariant() replaces the active player for the given side")
    void setPlayerVariantReplacesPlayer() throws Exception{
        // simulates switching top user from AI_HARD to (default val) to AI_EASY
        SwingUtilities.invokeAndWait(() ->
            game.onVariantSelected(PongToolbar.Side.TOP, PlayerVariant.AI_EASY)
        );

        assertEquals(PlayerVariant.AI_EASY, game.topVariant, "Top variant should be AI_EASY after selection");

        assertEquals("AI Easy", game.topPlayer.getName(), "Top player should be replaced with AI Easy instance");

    }

    // ──────────────────────────────────────────────────────────────────────────
    // Paddle collision
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[TODO] Ball hitting the top paddle reverses vertical direction downward")
    void ballHitsTopPaddleAndBouncesDown() {
        // TODO: implement
        // Hint: set ballY near TOP_PADDLE_Y + PADDLE_HEIGHT with ballVelY < 0,
        // set paddle X to overlap ball X, then call tickPlaying() and assert ballVelY > 0
    }

    @Test
    @DisplayName("[TODO] Ball hitting the bottom paddle reverses vertical direction upward")
    void ballHitsBottomPaddleAndBouncesUp() {
        // TODO: implement
        // Hint: set ballY near BOTTOM_PADDLE_Y with ballVelY > 0,
        // set paddle X to overlap ball X, then call tickPlaying() and assert ballVelY < 0
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Reset
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[TODO] resetGame() resets both scores to zero and returns to PAUSED state")
    void resetGameResetsScoresAndReturnsToPaused() {
        // TODO: implement
        // Hint: score some points, call game.resetGame() (package-private via reflection or
        // trigger via the 'R' key action), then assert topScore==0, bottomScore==0, gameState==PAUSED
    }

    // ──────────────────────────────────────────────────────────────────────────
    // createHardwarePlayer() static factory
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[TODO] createHardwarePlayer() returns null when manager is null")
    void createHardwarePlayerReturnsNullWhenManagerIsNull() {
        // TODO: implement
        // assertNull(PongGame.createHardwarePlayer(null))
    }

    @Test
    @DisplayName("[TODO] createHardwarePlayer() returns null when manager is not connected")
    void createHardwarePlayerReturnsNullWhenManagerNotConnected() {
        // TODO: implement
        // Hint: mock a SerialConnectionManager with isConnected() == false
    }

    @Test
    @DisplayName("[TODO] createHardwarePlayer() returns null when channel count is less than 2")
    void createHardwarePlayerReturnsNullWhenChannelCountLessThanTwo() {
        // TODO: implement
        // Hint: mock a connected manager with getDeviceChannelCount() == 1
    }

    @Test
    @DisplayName("[TODO] createHardwarePlayer() returns a PongHardwareAI when manager is connected with 2 channels")
    void createHardwarePlayerReturnsPlayerWhenFullyConnected() {
        // TODO: implement
        // Hint: mock a connected manager with getDeviceChannelCount() == 2,
        // then assertNotNull and assertInstanceOf(PongHardwareAI.class, result)
    }
}
