package edu.sjsu.spring2026.group32.hitthezone;

import edu.sjsu.spring2026.group32.annotations.GeneratedExcludeFromCoverage;
import edu.sjsu.spring2026.group32.hardware.serial.RealSerialDevice;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.hardware.signal.HardwareSignalSource;
import edu.sjsu.spring2026.group32.hardware.signal.NeuralSignalParser;
import edu.sjsu.spring2026.group32.hitthezone.ai.HitTheZoneHardwareAI;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneActionEffect;
import edu.sjsu.spring2026.group32.hitthezone.core.HitTheZoneEngine;
import edu.sjsu.spring2026.group32.hitthezone.core.HitTheZoneHardwareFactory;
import edu.sjsu.spring2026.group32.hitthezone.core.HitTheZonePlayerFactory;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneSnapshot;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;
import edu.sjsu.spring2026.group32.hitthezone.ui.HitTheZoneControlPanel;
import edu.sjsu.spring2026.group32.hitthezone.ui.HitTheZoneHudPanel;
import edu.sjsu.spring2026.group32.hitthezone.ui.HitTheZoneInputController;
import edu.sjsu.spring2026.group32.hitthezone.ui.HitTheZoneTrackPanel;
import edu.sjsu.spring2026.group32.player.model.BasePlayer;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

@GeneratedExcludeFromCoverage
public class HitTheZoneGame extends JFrame {
    private final List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players;
    private final SerialConnectionManager htzManager;
    private final HitTheZoneEngine engine;
    private final HitTheZoneHudPanel hudPanel;
    private final HitTheZoneTrackPanel trackPanel;
    private final HitTheZoneControlPanel controlPanel;
    private final HitTheZoneInputController inputController = new HitTheZoneInputController();
    private final Timer tick;
    private final SerialConnectionManager.SerialListener hardwareConnectionListener;

    private HitTheZoneHardwareAI hardwarePlayer;
    private boolean hardwareEverConnected;
    private boolean shutdownStarted;
    private String overrideInfoText;

    @GeneratedExcludeFromCoverage
    public HitTheZoneGame(List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players,
                          SerialConnectionManager htzManager) {
        super("Hit The Zone");
        this.players = new ArrayList<>(players);
        this.htzManager = htzManager;
        this.engine = new HitTheZoneEngine(this.players.size());
        this.hardwarePlayer = findHardwarePlayer();
        this.hardwareEverConnected = hardwarePlayer != null;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        hudPanel = new HitTheZoneHudPanel(htzManager);
        hudPanel.syncPlayers(this.players);
        add(hudPanel, BorderLayout.NORTH);

        trackPanel = new HitTheZoneTrackPanel();
        add(trackPanel, BorderLayout.CENTER);

        controlPanel = new HitTheZoneControlPanel(this::togglePause, this::resetGame, this::processScore);
        controlPanel.syncPlayers(this.players);
        add(controlPanel, BorderLayout.SOUTH);

        inputController.bindKeys(trackPanel, this::togglePause, this::resetGame);
        inputController.registerHumanPlayers(this.players);

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        trackPanel.requestFocusInWindow();

        togglePause();
        tick = new Timer(16, e -> onTick());
        tick.start();
        refreshUi(true);

        hardwareConnectionListener = new SerialConnectionManager.SerialListener() {
            @Override
            public void onConnected(String portName) {
                SwingUtilities.invokeLater(() -> handleHardwareConnected(portName));
            }

            @Override
            public void onDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> handleHardwareDisconnected(reason));
            }
        };

        if (htzManager != null) {
            htzManager.addListener(hardwareConnectionListener);
        }
    }

    @GeneratedExcludeFromCoverage
    public static HitTheZoneGame launchFromLauncher(SerialConnectionManager htzManager) {
        HitTheZoneGame frame = new HitTheZoneGame(HitTheZonePlayerFactory.createDefaultPlayers(htzManager), htzManager);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
        return frame;
    }

    @Override
    public void dispose() {
        shutdownForWindowClose();
        super.dispose();
    }

    private void shutdownForWindowClose() {
        if (shutdownStarted) {
            return;
        }
        shutdownStarted = true;

        if (htzManager != null) {
            htzManager.removeListener(hardwareConnectionListener);
        }

        tick.stop();
        inputController.unregisterHumanPlayers();
        if (!engine.isPaused()) {
            engine.togglePause();
        }
        stopHardwareInjection();
    }

    protected void onTick() {
        if (engine.isPaused()) {
            return;
        }

        engine.tickMotion(trackPanel.getWidth());
        HitTheZoneState state = new HitTheZoneState(engine.snapshot().inZone());

        for (int i = 0; i < players.size(); i++) {
            BasePlayer<HitTheZoneState, HitTheZoneAction> player = players.get(i);
            HitTheZoneAction action = player.getNextMove(state);
            HitTheZoneActionEffect effect = engine.handleAction(
                    i,
                    action,
                    player.getType(),
                    player instanceof HitTheZoneHardwareAI);

            if (effect == HitTheZoneActionEffect.PAUSE_REQUESTED) {
                togglePause();
            } else if (effect == HitTheZoneActionEffect.RESET_REQUESTED) {
                resetGame();
            }
        }

        refreshUi(false);
        trackPanel.repaint();
    }

    protected void processScore(int playerIndex) {
        engine.handleAction(
                playerIndex,
                HitTheZoneAction.SCORE,
                players.get(playerIndex).getType(),
                players.get(playerIndex) instanceof HitTheZoneHardwareAI);
        refreshUi(false);
        trackPanel.repaint();
    }

    @GeneratedExcludeFromCoverage
    protected void togglePause() {
        engine.togglePause();
        controlPanel.setPaused(engine.isPaused());
        if (engine.isPaused()) {
            overrideInfoText = "--- PAUSED  (Esc to resume | R to reset) ---";
            stopHardwareInjection();
        } else {
            overrideInfoText = null;
        }
        refreshUi(true);
    }

    protected void resetGame() {
        engine.resetMatch();
        overrideInfoText = null;
        if (engine.isPaused()) {
            engine.togglePause();
        }
        controlPanel.setPaused(engine.isPaused());
        refreshUi(true);
        trackPanel.repaint();
    }

    private void handleHardwareConnected(String portName) {
        if (shutdownStarted || htzManager == null || !htzManager.isConnected()) {
            return;
        }

        boolean addedPlayer = false;
        if (hardwarePlayer == null) {
            hardwarePlayer = createHardwarePlayer(htzManager);
            players.add(hardwarePlayer);
            engine.appendPlayer();
            addedPlayer = true;
        }

        if (hardwarePlayer != null) {
            hardwareEverConnected = true;
        }

        if (addedPlayer) {
            hudPanel.syncPlayers(players);
            controlPanel.syncPlayers(players);
            inputController.registerHumanPlayers(players);
            pack();
        }

        pauseForHardwareEvent((addedPlayer
                ? "Hardware player connected on "
                : "Hardware reconnected on ") + portName + ".");
    }

    private void handleHardwareDisconnected(String reason) {
        if (shutdownStarted || !hardwareEverConnected) {
            return;
        }

        stopHardwareInjection();
        pauseForHardwareEvent("Hardware disconnected: " + reason + ".");
    }

    private void pauseForHardwareEvent(String message) {
        if (!engine.isPaused()) {
            engine.togglePause();
            controlPanel.setPaused(true);
            stopHardwareInjection();
        }
        overrideInfoText = message + " Press Esc to resume.";
        refreshUi(true);
        trackPanel.repaint();
    }

    private void refreshUi(boolean includePausedLabels) {
        HitTheZoneSnapshot snapshot = engine.snapshot();
        String infoText = infoText(snapshot, includePausedLabels);
        hudPanel.refresh(snapshot, players, infoText);
        trackPanel.setViewModel(snapshot, players);
    }

    private String infoText(HitTheZoneSnapshot snapshot, boolean includePausedLabels) {
        if (snapshot.paused()) {
            return includePausedLabels
                    ? (overrideInfoText != null ? overrideInfoText : "--- PAUSED  (Esc to resume | R to reset) ---")
                    : (overrideInfoText != null ? overrideInfoText : "");
        }

        long secs = (snapshot.elapsedMs() / 1000) % 60;
        long mins = snapshot.elapsedMs() / 60_000;
        String timer = String.format("%02d:%02d", mins, secs);
        return String.format(
                "Time: %s   |   Total Passes: %d   |   Ball X: %d   |   %s",
                timer,
                snapshot.totalPasses(),
                snapshot.ballX(),
                snapshot.inZone() ? "IN ZONE" : "Out of Zone");
    }

    private HitTheZoneHardwareAI findHardwarePlayer() {
        for (BasePlayer<HitTheZoneState, HitTheZoneAction> player : players) {
            if (player instanceof HitTheZoneHardwareAI hardwareAi) {
                return hardwareAi;
            }
        }
        return null;
    }

    private void stopHardwareInjection() {
        for (BasePlayer<HitTheZoneState, HitTheZoneAction> player : players) {
            if (player instanceof HitTheZoneHardwareAI hardwareAi) {
                hardwareAi.stopInjectionOnly();
            }
        }
    }

    private static HitTheZoneHardwareAI createHardwarePlayer(SerialConnectionManager htzManager) {
        return HitTheZoneHardwareFactory.createHardwarePlayer(htzManager);
    }

    public void setBallSpeed(int ballSpeed) {
        engine.setBallSpeed(ballSpeed);
        refreshUi(true);
        trackPanel.repaint();
    }

    public void setZoneWidth(int zoneWidth) {
        engine.setZoneWidth(zoneWidth);
        refreshUi(true);
        trackPanel.repaint();
    }

    @GeneratedExcludeFromCoverage
    public static void main(String[] args) {
        SerialConnectionManager scm = new SerialConnectionManager(RealSerialDevice::getRealPorts);
        NeuralSignalParser parser = new NeuralSignalParser();
        HardwareSignalSource source = new HardwareSignalSource(scm, parser);
        HitTheZoneHardwareAI hardwarePlayer = new HitTheZoneHardwareAI("Neural", source, source);
        Runtime.getRuntime().addShutdownHook(new Thread(hardwarePlayer::close));

        List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players =
                HitTheZonePlayerFactory.createStandalonePlayers(hardwarePlayer);

        SwingUtilities.invokeLater(() -> {
            HitTheZoneGame game = new HitTheZoneGame(players, scm);
            game.setVisible(true);
        });
    }
}
