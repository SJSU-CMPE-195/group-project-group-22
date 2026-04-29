package edu.sjsu.spring2026.group32.pong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.*;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
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
    @DisplayName("Ball hitting the top paddle reverses vertical direction downward")
    void ballHitsTopPaddleAndBouncesDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> game.startCountdown());
        game.gameState = PongGame.GameState.PLAYING;

        // simulates positioning the ball below the top paddle's bottom edge, moving upward.
        // TOP_PADDLE_Y + PADDLE_HEIGHT is the bottom face of the top paddle.

        game.ballY    = PongGame.TOP_PADDLE_Y + PongGame.PADDLE_HEIGHT - 1;
        game.ballVelY = -5; // moving up toward top paddle

        // simulates centering the paddle directly over the ball so the X overlap condition is met

        game.ballX = PongGame.FIELD_WIDTH / 2;

        game.tickPlaying();

        assertTrue(game.ballVelY > 0, "Ball should bounce downward (positive velY) after hitting the top paddle");
    }

    @Test
    @DisplayName("Ball hitting the bottom paddle reverses vertical direction upward")
    void ballHitsBottomPaddleAndBouncesUp() throws Exception{
        SwingUtilities.invokeAndWait(() -> game.startCountdown());
        game.gameState = PongGame.GameState.PLAYING;

        // BOTTOM_PADDLE_Y is the top face of the bottom paddle.
        // ballY + BALL_SIZE >= BOTTOM_PADDLE_Y triggers the collision.
        game.ballY = PongGame.BOTTOM_PADDLE_Y - PongGame.BALL_SIZE + 1;
        game.ballVelY = 5; // moving down toward bottom paddle
        game.ballX = PongGame.FIELD_WIDTH / 2; // center — paddle starts centered

        game.tickPlaying();

        assertTrue(game.ballVelY < 0, "Ball should bounce upward (negative velY) after hitting the bottom paddle");
    }
    

    // ──────────────────────────────────────────────────────────────────────────
    // Reset
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetGame() resets both scores to zero and returns to PAUSED state")
    void resetGameResetsScoresAndReturnsToPaused() throws Exception{
        SwingUtilities.invokeAndWait(() -> game.startCountdown());
        game.gameState = PongGame.GameState.PLAYING;

        game.topScore = 3;
        game.bottomScore = 5;

        SwingUtilities.invokeAndWait(() -> game.startCountdown()); 
        game.topScore = 3;
        game.bottomScore = 5;

        SwingUtilities.invokeAndWait(() -> {
            game.topScore = 3;
            game.bottomScore = 5;
            game.gameState = PongGame.GameState.PLAYING;

            game.togglePause();

        });

        game.topScore = 3;
        game.bottomScore = 5;
        game.gameState = PongGame.GameState.PLAYING;

        SwingUtilities.invokeAndWait(() -> {
            game.getActionMap().get("game-reset").actionPerformed(new java.awt.event.ActionEvent(game, 0, ""));

        });

        assertEquals(0, game.topScore, "topScore should be 0 after reset");
        assertEquals(0, game.bottomScore, "bottomScore should be 0 after reset");
        assertEquals(PongGame.GameState.PAUSED, game.gameState, "gameState should be PAUSED after reset");

    }

    // ──────────────────────────────────────────────────────────────────────────
    // createHardwarePlayer() static factory
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createHardwarePlayer() returns null when manager is null")
    void createHardwarePlayerReturnsNullWhenManagerIsNull() {
        assertNull(PongGame.createHardwarePlayer(null));

    }

    @Test
    @DisplayName("createHardwarePlayer() returns null when manager is not connected")
    void createHardwarePlayerReturnsNullWhenManagerNotConnected() {
        SerialConnectionManager mockManager = mock(SerialConnectionManager.class);

        when(mockManager.isConnected()).thenReturn(false);

        assertNull(PongGame.createHardwarePlayer(mockManager));

    }

    @Test
    @DisplayName("createHardwarePlayer() returns null when channel count is less than 2")
    void createHardwarePlayerReturnsNullWhenChannelCountLessThanTwo() {
        SerialConnectionManager mockManager = mock(SerialConnectionManager.class);

        when(mockManager.isConnected()).thenReturn(true);
        when(mockManager.getDeviceChannelCount()).thenReturn(1);

        assertNull(PongGame.createHardwarePlayer(mockManager));

    }

    @Test
    @DisplayName("createHardwarePlayer() returns a PongHardwareAI when manager is connected with 2 channels")
    void createHardwarePlayerReturnsPlayerWhenFullyConnected() {
        SerialConnectionManager mockManager = mock(SerialConnectionManager.class);

        when(mockManager.isConnected()).thenReturn(true);
        when(mockManager.getDeviceChannelCount()).thenReturn(2);

        when(mockManager.getNextLine()).thenReturn("1000,0,0,0");

        PongHardwareAI result = PongGame.createHardwarePlayer(mockManager);

        assertNotNull(result);
        assertInstanceOf(PongHardwareAI.class, result);
    }
}
