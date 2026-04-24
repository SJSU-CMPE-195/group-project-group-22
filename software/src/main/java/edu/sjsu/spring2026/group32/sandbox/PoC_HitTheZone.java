package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.annotations.GeneratedExcludeFromCoverage;
import edu.sjsu.spring2026.group32.hardware.HardwareSignalSource;
import edu.sjsu.spring2026.group32.hardware.NeuralSignalParser;
import edu.sjsu.spring2026.group32.hardware.serial.RealSerialDevice;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.launcher.ConnectionStatusPanel;
import edu.sjsu.spring2026.group32.player.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PoC_HitTheZone extends JFrame {

    // ---- Layout constants ------------------------------------------------
    static final int WIDTH      = 820;
    static final int HEIGHT     = 400;
    static final int BALL_DIAM  = 20;
    static final int START_X    = 40;
    static final int TRACK_Y    = 130;
    static final int SPEED      = 5;
    static final int ZONE_WIDTH = 80;
    static final int ZONE_START = (WIDTH - ZONE_WIDTH) / 2;

    static final Color[] PLAYER_COLORS = {
            new Color(30,  120, 220),
            new Color(34,  160,  80),
            new Color(200,  80,  80),
            new Color(160,  80, 200),
    };

    // ---- Players ---------------------------------------------------------
    private final List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players;

    // Package-private so same-package tests (PoCTest) can inspect values directly.
    final int[]              hits;
    final int[]              attempts;
    final boolean[]          canScore;
    /**
     * Debounces non-scoring control actions so held keys do not repeatedly
     * pause or reset the game. SCORE is intentionally excluded so players can
     * land multiple hits during a single zone pass.
     */
    private final HitTheZoneAction[] lastActions;

    // ---- Shared game state -----------------------------------------------
    protected int     ballX       = START_X;
    protected int     direction   = SPEED;
    protected int     totalPasses = 0;
    protected boolean inZone      = false;
    protected boolean isPaused    = false;
    /** Total game time in milliseconds — frozen while paused. */
    protected long    elapsedMs   = 0;
    private boolean   shutdownStarted = false;

    // ---- Swing -----------------------------------------------------------
    private  final JLabel[]        playerLabels;
    private  final List<JButton>   humanScoreButtons = new ArrayList<>();
    private  final JLabel          infoLabel   = new JLabel("", SwingConstants.CENTER);
    private  final JButton    pauseButton = new JButton("Pause (Esc)");
    protected final GamePanel gamePanel   = new GamePanel();
    protected final Timer     tick;

    // ======================================================================
    // Headless constructor (unit tests)
    // ======================================================================

    protected PoC_HitTheZone(boolean headless,
                             List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players) {
        super("Hit The Zone");
        this.players      = players;
        int n             = players.size();
        this.hits         = new int[n];
        this.attempts     = new int[n];
        this.canScore     = new boolean[n];
        this.lastActions  = new HitTheZoneAction[n];
        this.playerLabels = new JLabel[n];
        this.tick         = null;
    }

    // ======================================================================
    // Full GUI constructor
    // ======================================================================

    /**
     * Convenience constructor for launching without a hardware connection.
     * Equivalent to {@link #PoC_HitTheZone(List, SerialConnectionManager)
     * PoC_HitTheZone(players, null)}.
     */
    @GeneratedExcludeFromCoverage
    public PoC_HitTheZone(List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players) {
        this(players, null);
    }

    /**
     * Full GUI constructor.
     *
     * @param players    the player list (software AI, human, hardware AI, etc.)
     * @param htzManager the HTZ serial connection, or {@code null} if no hardware
     *                   is connected; used only to populate the hardware-status bar
     */
    @GeneratedExcludeFromCoverage
    public PoC_HitTheZone(List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players,
                          SerialConnectionManager htzManager) {
        super("Hit The Zone");

        this.players     = players;
        int n            = players.size();
        this.hits        = new int[n];
        this.attempts    = new int[n];
        this.canScore    = new boolean[n];
        this.lastActions = new HitTheZoneAction[n];

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ---- Top HUD -----------------------------------------------------
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

        // Hardware status bar sits at the very top of the HUD.
        ConnectionStatusPanel statusPanel = new ConnectionStatusPanel();
        statusPanel.addDevice("HTZ", htzManager);
        topPanel.add(statusPanel, BorderLayout.NORTH);

        playerLabels = new JLabel[n];
        JPanel playerRow = new JPanel(new GridLayout(n, 1, 0, 2));
        for (int i = 0; i < n; i++) {
            playerLabels[i] = new JLabel("", SwingConstants.CENTER);
            playerLabels[i].setFont(playerLabels[i].getFont().deriveFont(Font.BOLD, 13f));
            playerLabels[i].setForeground(playerColor(i));
            playerRow.add(playerLabels[i]);
        }
        topPanel.add(playerRow, BorderLayout.CENTER);

        infoLabel.setFont(infoLabel.getFont().deriveFont(12f));
        topPanel.add(infoLabel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // ---- Game panel --------------------------------------------------
        gamePanel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        gamePanel.setFocusable(true);
        add(gamePanel, BorderLayout.CENTER);

        // FIX: Register human players via KeyboardFocusManager instead of
        // gamePanel.addKeyListener(). KeyboardFocusManager dispatches key
        // events application-wide regardless of which component has focus,
        // so the human's input is never silently dropped when a button or
        // another component steals focus.
        for (BasePlayer<?, ?> p : players) {
            if (p instanceof KeyListener kl) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .addKeyEventDispatcher(e -> {
                            if (e.getID() == KeyEvent.KEY_PRESSED)  kl.keyPressed(e);
                            if (e.getID() == KeyEvent.KEY_RELEASED) kl.keyReleased(e);
                            return false; // don't consume — let Esc/R bindings still fire
                        });
            }
        }

        // ---- Global key bindings (pause / reset) -------------------------
        setupKeyBindings();

        // ---- Bottom controls ---------------------------------------------
        pauseButton.setFocusable(false);
        pauseButton.addActionListener(e -> togglePause());

        JButton resetButton = new JButton("Reset (R)");
        resetButton.setFocusable(false);
        resetButton.addActionListener(e -> resetGame());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        bottom.add(pauseButton);
        bottom.add(resetButton);
        // Add a dedicated Score button for every human (KeyListener) player.
        // Using instanceof KeyListener rather than getType() == HUMAN because
        // HumanPlayer.getType() incorrectly returns SOFTWARE (upstream bug).
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i) instanceof KeyListener) {
                final int idx = i;
                JButton scoreBtn = new JButton("Score (Space) — " + players.get(i).getName());
                scoreBtn.setFocusable(false);  // prevents spacebar double-firing via focus
                scoreBtn.setForeground(playerColor(i));
                scoreBtn.setFont(scoreBtn.getFont().deriveFont(Font.BOLD));
                scoreBtn.addActionListener(e -> processScore(idx));
                humanScoreButtons.add(scoreBtn);
                bottom.add(scoreBtn);
            }
        }
        add(bottom, BorderLayout.SOUTH);

        // ---- Launch ------------------------------------------------------
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);
        gamePanel.requestFocusInWindow();

        updateHud();
        tick = new Timer(16, e -> onTick());
        tick.start();
        togglePause();
    }

    /**
     * Launcher-facing entry point that keeps Hit The Zone player assembly in
     * the game module.
     */
    @GeneratedExcludeFromCoverage
    public static PoC_HitTheZone launchFromLauncher(SerialConnectionManager htzManager) {
        PoC_HitTheZone frame = new PoC_HitTheZone(createDefaultPlayers(htzManager), htzManager);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
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

        if (tick != null) {
            tick.stop();
        }
        isPaused = true;

        for (BasePlayer<HitTheZoneState, HitTheZoneAction> player : players) {
            if (player instanceof HitTheZoneHardwareAI hardwarePlayer) {
                hardwarePlayer.stopInjectionOnly();
            }
        }
    }

    /**
     * Builds the default roster for launcher-driven runs.
     */
    static List<BasePlayer<HitTheZoneState, HitTheZoneAction>> createDefaultPlayers(
            SerialConnectionManager htzManager) {
        List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players = new ArrayList<>();
        players.add(new HitTheZoneSoftwareAI("Bot Alpha", 3));
        players.add(new HitTheZoneSoftwareAI("Bot Beta", 9));

        if (htzManager != null && htzManager.isConnected()) {
            NeuralSignalParser parser = new NeuralSignalParser(0);
            HardwareSignalSource src = new HardwareSignalSource(htzManager, parser);
            players.add(new HitTheZoneHardwareAI("Neural", src, src));
        }

        Map<Integer, HitTheZoneAction> bindings =
                Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE);
        players.add(new HumanPlayer<>("Human", bindings, null));
        return players;
    }

    // ======================================================================
    // Key bindings
    // ======================================================================

    private void setupKeyBindings() {
        InputMap  im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = gamePanel.getActionMap();

        im.put(KeyStroke.getKeyStroke("ESCAPE"), "pauseAction");
        am.put("pauseAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { togglePause(); }
        });

        im.put(KeyStroke.getKeyStroke("R"), "resetAction");
        am.put("resetAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { resetGame(); }
        });
    }

    // ======================================================================
    // Game loop
    // ======================================================================

    protected void onTick() {
        if (isPaused) return;

        elapsedMs += 16;
        moveBall();

        HitTheZoneState state = new HitTheZoneState(inZone);

        for (int i = 0; i < players.size(); i++) {
            HitTheZoneAction action = players.get(i).getNextMove(state);

            if (action == HitTheZoneAction.SCORE) {
                boolean isHumanScorePress = players.get(i).getType() == PlayerType.HUMAN;
                if (!isHumanScorePress || action != lastActions[i]) {
                    processScore(i);
                }
            } else if (action != null && action != lastActions[i]) {
                if (action == HitTheZoneAction.PAUSE) {
                    togglePause();
                } else if (action == HitTheZoneAction.RESET) {
                    resetGame();
                }
            }
            lastActions[i] = action;
        }

        updateHud();
        gamePanel.repaint();
    }

    // ---- Ball physics ----------------------------------------------------

    private void moveBall() {
        ballX += direction;

        int panelWidth = gamePanel.getWidth() > 0 ? gamePanel.getWidth() : WIDTH;
        int rightBound = panelWidth - BALL_DIAM;

        if      (ballX <= 0)          { ballX = 0;          direction = SPEED; }
        else if (ballX >= rightBound) { ballX = rightBound; direction = -SPEED; }

        int     centerX   = ballX + BALL_DIAM / 2;
        boolean nowInZone = centerX >= ZONE_START && centerX <= (ZONE_START + ZONE_WIDTH);

        if (!inZone && nowInZone) {
            // Zone entry: arm scoring and clear debounced control actions.
            totalPasses++;
            for (int i = 0; i < canScore.length; i++) {
                canScore[i]    = true;
                lastActions[i] = null;
            }
        } else if (inZone && !nowInZone) {
            // Zone exit: explicitly disarm canScore so the state machine is
            // unambiguous. Without this, a player who never scored this pass
            // retains canScore=true across the gap between passes, and a
            // mis-timed edge-detection reset on re-entry could fire an
            // out-of-zone press as a hit.
            Arrays.fill(canScore, false);
        }

        inZone = nowInZone;
    }

    // ======================================================================
    // Game actions
    // ======================================================================

    protected void processScore(int playerIndex) {
        if (isPaused) return;

        attempts[playerIndex]++;

        int     centerX = ballX + BALL_DIAM / 2;
        boolean inside  = centerX >= ZONE_START && centerX <= (ZONE_START + ZONE_WIDTH);

        if (inside && canScore[playerIndex]) {
            hits[playerIndex]++;
        }
    }

    @GeneratedExcludeFromCoverage
    protected void togglePause() {
        isPaused = !isPaused;
        pauseButton.setText(isPaused ? "Resume (Esc)" : "Pause (Esc)");
        humanScoreButtons.forEach(b -> b.setEnabled(!isPaused));
        if (isPaused) {
            infoLabel.setText("--- PAUSED  (Esc to resume · R to reset) ---");
        } else {
            updateHud();
        }
    }

    protected void resetGame() {
        ballX       = START_X;
        direction   = SPEED;
        totalPasses = 0;
        inZone      = false;
        elapsedMs   = 0;

        for (int i = 0; i < players.size(); i++) {
            hits[i]        = 0;
            attempts[i]    = 0;
            canScore[i]    = false;
            lastActions[i] = null;
        }

        if (isPaused) togglePause();

        updateHud();
        gamePanel.repaint();
    }

    // ======================================================================
    // HUD
    // ======================================================================

    @GeneratedExcludeFromCoverage
    protected void updateHud() {
        if (isPaused) return;

        // Per-player row — minified:
        // Name [TYPE]  |  Hits: X / Y  |  Accuracy: X/Y (Z%)  |  Hits/Pass: X/P (Z%)
        for (int i = 0; i < players.size(); i++) {
            int    h   = hits[i];
            int    a   = attempts[i];
            String acc      = a == 0 ? "—" : String.format("%d/%d (%.0f%%)", h, a, (h * 100.0) / a);
            String hitsPass = totalPasses == 0 ? "—" : String.format("%.2f", (double) h / totalPasses);

            playerLabels[i].setText(String.format(
                    "%-12s [%s]   Hits: %d / %d   Accuracy: %s   Hits/Pass: %s",
                    players.get(i).getName(),
                    players.get(i).getType(),
                    h, a, acc, hitsPass));
        }

        // Global stats row — elapsed time frozen while paused
        long   secs  = (elapsedMs / 1000) % 60;
        long   mins  = (elapsedMs / 60_000);
        String timer = String.format("%02d:%02d", mins, secs);

        infoLabel.setText(String.format(
                "Time: %s   |   Total Passes: %d   |   Ball X: %d   |   %s",
                timer, totalPasses, ballX, inZone ? "★  IN ZONE  ★" : "Out of Zone"));
    }

    // ======================================================================
    // Rendering
    // ======================================================================

    protected class GamePanel extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            final int trackH   = 44;
            final int trackTop = TRACK_Y - trackH / 2;

            g2.setColor(new Color(210, 210, 210));
            g2.fillRect(0, trackTop, getWidth(), trackH);

            g2.setColor(new Color(255, 228, 80));
            g2.fillRect(ZONE_START, trackTop, ZONE_WIDTH, trackH);
            g2.setColor(new Color(160, 120, 0));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(ZONE_START, trackTop, ZONE_WIDTH, trackH);

            // Per-player hit-rate bars.
            // Scale is dynamic: the player with the highest hits/pass fills the
            // bar completely; all others are drawn relative to that ceiling.
            // This keeps every bar within its border regardless of how many
            // hits per pass players accumulate.
            final int barH    = 10;
            final int barGap  = 4;
            int       barTopY = trackTop + trackH + 10;

            double maxRate = 0.0;
            if (totalPasses > 0) {
                for (int i = 0; i < players.size(); i++)
                    maxRate = Math.max(maxRate, hits[i] / (double) totalPasses);
            }

            for (int i = 0; i < players.size(); i++) {
                double rate   = (totalPasses == 0 || maxRate == 0) ? 0.0
                        : (hits[i] / (double) totalPasses) / maxRate;
                int    filled = (int) (rate * ZONE_WIDTH);
                Color  c      = playerColor(i);

                g2.setColor(c.darker());
                g2.fillRect(ZONE_START, barTopY, ZONE_WIDTH, barH);
                g2.setColor(c);
                g2.fillRect(ZONE_START, barTopY, filled, barH);
                g2.setColor(Color.DARK_GRAY);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(ZONE_START, barTopY, ZONE_WIDTH, barH);
                g2.setColor(c);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
                String barLabel = totalPasses == 0
                        ? players.get(i).getName()
                        : String.format("%s (%.2f hits/pass)", players.get(i).getName(),
                        hits[i] / (double) totalPasses);
                g2.drawString(barLabel, ZONE_START + ZONE_WIDTH + 6, barTopY + barH - 1);

                barTopY += barH + barGap;
            }

            // Ball
            g2.setColor(Color.RED);
            g2.fillOval(ballX, TRACK_Y - BALL_DIAM / 2, BALL_DIAM, BALL_DIAM);
            g2.setColor(new Color(120, 0, 0));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(ballX, TRACK_Y - BALL_DIAM / 2, BALL_DIAM, BALL_DIAM);

            // Zone label
            g2.setColor(new Color(100, 80, 0));
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
            FontMetrics fm  = g2.getFontMetrics();
            String      lbl = "ZONE";
            g2.drawString(lbl,
                    ZONE_START + (ZONE_WIDTH - fm.stringWidth(lbl)) / 2,
                    trackTop - 4);
        }
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private static Color playerColor(int index) {
        return index < PLAYER_COLORS.length ? PLAYER_COLORS[index] : Color.DARK_GRAY;
    }

    // ======================================================================
    // Entry point
    // ======================================================================

    public static void main(String[] args) {
        Map<Integer, HitTheZoneAction> bindings =
                Map.of(KeyEvent.VK_SPACE, HitTheZoneAction.SCORE);

        // Build the hardware stack: serial manager → signal parser → source → player.
        // If no ESP32 is connected, HardwareSignalSource returns 0.0 V and the
        // hardware player simply never scores — safe graceful degradation.
        SerialConnectionManager scm    = new SerialConnectionManager(RealSerialDevice::getRealPorts);
        NeuralSignalParser      parser = new NeuralSignalParser();
        HardwareSignalSource    src    = new HardwareSignalSource(scm, parser);
        // src implements both BaseSignalSource (spike reading) and VoltageInjector
        // (inject/stop commands), so it is passed for both roles.
        HitTheZoneHardwareAI    hwPlayer = new HitTheZoneHardwareAI("Neural", src, src);

        // Release the serial port when the JVM exits (window close or Ctrl-C).
        Runtime.getRuntime().addShutdownHook(new Thread(hwPlayer::close));

        List<BasePlayer<HitTheZoneState, HitTheZoneAction>> players = List.of(
                new HitTheZoneSoftwareAI("Bot Alpha", 3),
                new HitTheZoneSoftwareAI("Bot Beta",  9),
                hwPlayer,
                new HumanPlayer<>("Human", bindings, null)
        );

        // scm is still passed for the hardware-status bar in the HUD.
        SwingUtilities.invokeLater(() -> new PoC_HitTheZone(players, scm));
    }
}
