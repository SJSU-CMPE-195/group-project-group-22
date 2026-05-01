package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.annotations.GeneratedExcludeFromCoverage;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.player.HumanPlayer;
import edu.sjsu.spring2026.group32.player.model.BasePlayer;
import edu.sjsu.spring2026.group32.pong.ai.PongHardwareAI;
import edu.sjsu.spring2026.group32.pong.core.PongEngine;
import edu.sjsu.spring2026.group32.pong.model.PongGameState;
import edu.sjsu.spring2026.group32.pong.core.PongHardwareFactory;
import edu.sjsu.spring2026.group32.pong.core.PongPlayerFactory;
import edu.sjsu.spring2026.group32.pong.model.PongTickResult;
import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;
import edu.sjsu.spring2026.group32.pong.model.PongAction;
import edu.sjsu.spring2026.group32.pong.model.PongState;
import edu.sjsu.spring2026.group32.pong.ui.PongCanvas;
import edu.sjsu.spring2026.group32.pong.ui.PongFrame;
import edu.sjsu.spring2026.group32.pong.ui.PongInputController;
import edu.sjsu.spring2026.group32.pong.ui.PongToolbar;
import edu.sjsu.spring2026.group32.pong.ui.Scoreboard;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

/**
 * Coordinator for Pong's engine, players, and Swing widgets.
 */
public class PongGame extends JPanel {
    static final PlayerVariant[] TOP_VARIANTS = {
            PlayerVariant.HARDWARE, PlayerVariant.AI_EASY, PlayerVariant.AI_HARD
    };
    static final PlayerVariant[] BOTTOM_VARIANTS = {
            PlayerVariant.HUMAN, PlayerVariant.AI_EASY, PlayerVariant.AI_HARD
    };

    private static final PlayerVariant HARDWARE_FALLBACK_VARIANT = PlayerVariant.AI_HARD;
    private static final String HUMAN_CONTROLS_HINT = "Human controls: use Left and Right arrow keys to move the paddle.";

    private final PongEngine engine = new PongEngine();
    private final Scoreboard scoreboard = new Scoreboard();
    private final PongInputController inputController = new PongInputController();
    private final SerialConnectionManager pongManager;
    private final PongToolbar topToolbar;
    private final PongToolbar bottomToolbar;
    private final PongCanvas canvas;
    private final SerialConnectionManager.SerialListener hardwareConnectionListener;

    private PlayerVariant topVariant;
    private PlayerVariant bottomVariant;
    private BasePlayer<PongState, PongAction> topPlayer;
    private BasePlayer<PongState, PongAction> bottomPlayer;
    private PongHardwareAI hardwarePlayer;

    private int lastDisplayedCh1 = -1;
    private int lastDisplayedCh2 = -1;
    private volatile boolean running;

    public PongGame(PongHardwareAI hardwarePlayer, SerialConnectionManager pongManager) {
        this.hardwarePlayer = hardwarePlayer;
        this.pongManager = pongManager;

        boolean hardwareAvailable = hardwarePlayer != null
                && pongManager != null
                && pongManager.isConnected();
        topVariant = hardwareAvailable ? PlayerVariant.HARDWARE : PlayerVariant.AI_EASY;
        bottomVariant = PlayerVariant.AI_HARD;

        setLayout(new BorderLayout());
        setBackground(java.awt.Color.BLACK);
        setFocusable(true);

        topToolbar = new PongToolbar(PongToolbar.Side.TOP, TOP_VARIANTS, topVariant, hardwareAvailable, scoreboard);
        bottomToolbar = new PongToolbar(PongToolbar.Side.BOTTOM, BOTTOM_VARIANTS, bottomVariant, hardwareAvailable, scoreboard);
        canvas = new PongCanvas(
                this::togglePause,
                this::requestReset,
                this::toggleConstantSpeed,
                this::setBallSpeedLevel);

        topToolbar.setOnVariantChanged(v -> onVariantSelected(PongToolbar.Side.TOP, v));
        bottomToolbar.setOnVariantChanged(v -> onVariantSelected(PongToolbar.Side.BOTTOM, v));

        add(topToolbar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(bottomToolbar, BorderLayout.SOUTH);

        topPlayer = createPlayer(topVariant);
        bottomPlayer = createPlayer(bottomVariant);
        syncHardwareInjection();
        wireHumanPlayer();
        lockToolbars(false);
        topToolbar.setHardwareCountsVisible(topVariant == PlayerVariant.HARDWARE);
        refreshCanvas();

        inputController.bindGameKeys(
                this,
                this::togglePause,
                this::requestReset,
                this::toggleConstantSpeed,
                this::setBallSpeedLevel,
                () -> engine.getGameState() == PongGameState.PAUSED,
                this::refreshCanvas);

        hardwareConnectionListener = new SerialConnectionManager.SerialListener() {
            @Override
            public void onConnected(String portName) {
                SwingUtilities.invokeLater(() -> handleHardwareConnected(portName));
            }

            @Override
            public void onInfoUpdated(String deviceName, int channelCount) {
                SwingUtilities.invokeLater(() -> handleHardwareInfoUpdated(channelCount));
            }

            @Override
            public void onDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> handleHardwareDisconnected(reason));
            }
        };

        if (pongManager != null) {
            pongManager.addListener(hardwareConnectionListener);
        }
    }

    @GeneratedExcludeFromCoverage
    public static javax.swing.JFrame launchFromLauncher(SerialConnectionManager pongManager) {
        return PongFrame.launchFromLauncher(pongManager);
    }

    static PongHardwareAI createHardwarePlayer(SerialConnectionManager pongManager) {
        return PongHardwareFactory.createHardwarePlayer(pongManager);
    }

    public void start() {
        running = true;
        Thread gameThread = new Thread(this::gameLoop, "pong-loop");
        gameThread.setDaemon(true);
        gameThread.start();
        requestFocusInWindow();
    }

    public void stop() {
        running = false;
    }

    public void stopHardwareInjection() {
        if (hardwarePlayer != null) {
            hardwarePlayer.stopInjectionOnly();
        }
    }

    public void shutdownHardwareListener() {
        if (pongManager != null) {
            pongManager.removeListener(hardwareConnectionListener);
        }
    }

    void onVariantSelected(PongToolbar.Side side, PlayerVariant chosen) {
        if (side == PongToolbar.Side.TOP) {
            topVariant = chosen;
            topToolbar.setHardwareCountsVisible(topVariant == PlayerVariant.HARDWARE);
        } else {
            bottomVariant = chosen;
        }

        rebuildPlayers();
        resetEventCounts();
        engine.resetBall();
        refreshCanvas();
    }

    void tickCountdown(long now) {
        engine.tickCountdown(now);
    }

    void tickPlaying() {
        if (engine.snapshot().ballVelX() == 0 && engine.snapshot().ballVelY() == 0) {
            stopHardwareInjection();
            return;
        }

        PongAction topAction = topPlayer.getNextMove(engine.topPlayerState());
        PongAction bottomAction = bottomPlayer.getNextMove(engine.bottomPlayerState());

        PongTickResult result = engine.tickPlaying(
                topAction,
                bottomAction,
                topPlayer instanceof PongHardwareAI,
                bottomPlayer instanceof PongHardwareAI);

        if (result == PongTickResult.NONE) {
            return;
        }

        if (result == PongTickResult.RALLY_RESET) {
            resetEventCounts();
            return;
        }

        if (result == PongTickResult.TOP_SCORED) {
            scoreboard.recordPoint(topVariant, bottomVariant);
        } else if (result == PongTickResult.BOTTOM_SCORED) {
            scoreboard.recordPoint(bottomVariant, topVariant);
        }

        resetEventCounts();
        startCountdown();
    }

    void togglePause() {
        if (engine.getGameState() == PongGameState.PAUSED) {
            clearPauseStatusMessage();
            startCountdown();
        } else {
            stopHardwareInjection();
            engine.pause();
            lockToolbars(false);
            refreshCanvas();
        }
    }

    void startCountdown() {
        stopHardwareInjection();
        clearPauseStatusMessage();
        engine.startCountdown(System.currentTimeMillis());
        lockToolbars(true);
        refreshCanvas();
    }

    private void resetGame() {
        stopHardwareInjection();
        resetEventCounts();
        clearPauseStatusMessage();
        engine.resetMatch();
        lockToolbars(false);
        refreshCanvas();
    }

    private void requestReset() {
        if (engine.getGameState() != PongGameState.PAUSED) {
            return;
        }
        resetGame();
    }

    private void gameLoop() {
        while (running) {
            long now = System.currentTimeMillis();
            if (engine.getGameState() == PongGameState.COUNTDOWN) {
                tickCountdown(now);
            } else if (engine.getGameState() == PongGameState.PLAYING) {
                tickPlaying();
            }

            updateEventDisplay();
            refreshCanvas();
            canvas.repaint();

            try {
                Thread.sleep(16);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private BasePlayer<PongState, PongAction> createPlayer(PlayerVariant variant) {
        return PongPlayerFactory.createPlayer(variant, hardwarePlayer);
    }

    private void rebuildPlayers() {
        topPlayer = createPlayer(topVariant);
        bottomPlayer = createPlayer(bottomVariant);
        syncHardwareInjection();
        wireHumanPlayer();
    }

    private void wireHumanPlayer() {
        HumanPlayer<PongState, PongAction> humanPlayer = null;
        if (topPlayer instanceof HumanPlayer<?, ?> humanTop) {
            @SuppressWarnings("unchecked")
            HumanPlayer<PongState, PongAction> cast = (HumanPlayer<PongState, PongAction>) humanTop;
            humanPlayer = cast;
        } else if (bottomPlayer instanceof HumanPlayer<?, ?> humanBottom) {
            @SuppressWarnings("unchecked")
            HumanPlayer<PongState, PongAction> cast = (HumanPlayer<PongState, PongAction>) humanBottom;
            humanPlayer = cast;
        }
        inputController.wireHumanPlayer(this, humanPlayer);
        canvas.setBottomHintMessage(humanPlayer != null ? HUMAN_CONTROLS_HINT : null);
    }

    private void handleHardwareDisconnected(String reason) {
        stopHardwareInjection();
        topToolbar.setHardwareAvailable(false);
        bottomToolbar.setHardwareAvailable(false);

        boolean fellBack = false;
        if (topVariant == PlayerVariant.HARDWARE) {
            topVariant = HARDWARE_FALLBACK_VARIANT;
            topToolbar.setSelectedVariant(topVariant);
            topToolbar.setHardwareCountsVisible(false);
            fellBack = true;
        }
        if (bottomVariant == PlayerVariant.HARDWARE) {
            bottomVariant = HARDWARE_FALLBACK_VARIANT;
            bottomToolbar.setSelectedVariant(bottomVariant);
            fellBack = true;
        }

        if (fellBack) {
            rebuildPlayers();
            resetEventCounts();
            engine.resetBall();
        }

        pauseForHardwareEvent("Hardware disconnected: " + reason);
    }

    private void handleHardwareConnected(String portName) {
        pauseForHardwareEvent("Hardware reconnected on " + portName);
    }

    private void handleHardwareInfoUpdated(int channelCount) {
        if (channelCount < 2) {
            topToolbar.setHardwareAvailable(false);
            bottomToolbar.setHardwareAvailable(false);
            return;
        }

        if (hardwarePlayer == null) {
            hardwarePlayer = createHardwarePlayer(pongManager);
            rebuildPlayers();
        }

        boolean available = hardwarePlayer != null;
        topToolbar.setHardwareAvailable(available);
        bottomToolbar.setHardwareAvailable(available);
        refreshCanvas();
    }

    private void pauseForHardwareEvent(String message) {
        if (engine.getGameState() != PongGameState.PAUSED) {
            stopHardwareInjection();
            engine.pause();
            lockToolbars(false);
        }
        canvas.setStatusMessage(message + ". Press ESC to resume.");
        refreshCanvas();
        canvas.repaint();
    }

    private void toggleConstantSpeed() {
        engine.toggleConstantSpeed();
        refreshCanvas();
    }

    private void setBallSpeedLevel(int level) {
        engine.setBallSpeedLevel(level);
        refreshCanvas();
    }

    private void syncHardwareInjection() {
        if (hardwarePlayer == null) {
            return;
        }
        boolean selected = topVariant == PlayerVariant.HARDWARE || bottomVariant == PlayerVariant.HARDWARE;
        hardwarePlayer.setInjectionEnabled(selected);
    }

    private void lockToolbars(boolean lock) {
        SwingUtilities.invokeLater(() -> {
            topToolbar.setSelectionLocked(lock);
            bottomToolbar.setSelectionLocked(lock);
        });
    }

    private void clearPauseStatusMessage() {
        canvas.setStatusMessage(null);
    }

    private void resetEventCounts() {
        lastDisplayedCh1 = -1;
        lastDisplayedCh2 = -1;
        if (hardwarePlayer != null) {
            hardwarePlayer.resetSpikeCount();
        }
    }

    private void updateEventDisplay() {
        if (topVariant != PlayerVariant.HARDWARE || hardwarePlayer == null) {
            return;
        }
        int ch1 = hardwarePlayer.getCh1SpikeCount();
        int ch2 = hardwarePlayer.getCh2SpikeCount();
        if (ch1 != lastDisplayedCh1 || ch2 != lastDisplayedCh2) {
            lastDisplayedCh1 = ch1;
            lastDisplayedCh2 = ch2;
            SwingUtilities.invokeLater(() -> topToolbar.setEventCounts(ch1, ch2));
        }
    }

    private void refreshCanvas() {
        canvas.setSnapshot(engine.snapshot(), topPlayer.getName(), bottomPlayer.getName());
    }

    @GeneratedExcludeFromCoverage
    public static void main(String[] args) {
        SwingUtilities.invokeLater(PongFrame::launchStandalone);
    }
}
