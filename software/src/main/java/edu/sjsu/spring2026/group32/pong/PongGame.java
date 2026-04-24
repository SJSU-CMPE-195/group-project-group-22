package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.hardware.HardwareSignalSource;
import edu.sjsu.spring2026.group32.hardware.NeuralSignalParser;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.launcher.ConnectionStatusPanel;
import edu.sjsu.spring2026.group32.player.BasePlayer;
import edu.sjsu.spring2026.group32.player.HumanPlayer;
import edu.sjsu.spring2026.group32.pong.ui.PongToolbar;
import edu.sjsu.spring2026.group32.pong.ui.Scoreboard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;

/**
 * Vertical Pong game panel.
 *
 * <p>Paddles sit at the top and bottom of the field and slide horizontally.
 * The ball travels up and down, bouncing off left/right walls and the paddles.
 *
 * <h3>State machine</h3>
 * <pre>
 *   PAUSED -ESC-> COUNTDOWN -(2 s)-> PLAYING
 *     ^                                  |
 *     +------------------ESC-------------+
 *   PLAYING -(ball out)-> COUNTDOWN -> PLAYING
 *   R (any state) -> PAUSED (scores reset)
 *   PAUSED + variant change -> stays PAUSED (players rebuilt, ball reset)
 * </pre>
 *
 * <h3>Key bindings (WHEN_IN_FOCUSED_WINDOW)</h3>
 * <ul>
 *   <li>ESC   - toggle pause / resume</li>
 *   <li>R     - reset scores, return to PAUSED</li>
 *   <li>LEFT / RIGHT arrow - human player paddle</li>
 * </ul>
 */
public class PongGame extends JPanel {

    // -------------------------------------------------------------------------
    // Field / paddle / ball constants
    // -------------------------------------------------------------------------

    public static final int FIELD_WIDTH   = 600;
    public static final int FIELD_HEIGHT  = 460;
    public static final int PADDLE_WIDTH  = 80;
    public static final int PADDLE_HEIGHT = 12;
    public static final int BALL_SIZE     = 12;

    private static final int  PADDLE_MARGIN  = 28;
    static  final int  TOP_PADDLE_Y    = PADDLE_MARGIN;
    static  final int  BOTTOM_PADDLE_Y = FIELD_HEIGHT - PADDLE_MARGIN - PADDLE_HEIGHT;

    private static final int  PADDLE_SPEED   = 7;
    private static final long COUNTDOWN_MS   = 2_000;

    /** Ball X/Y velocities for speed levels 1-5 (index 0-4). Default level: 2 (index 1). */
    private static final int[] SPEED_VEL_X = { 3, 4, 5, 6, 7 };
    private static final int[] SPEED_VEL_Y = { 3, 5, 7, 9, 11 };

    // -------------------------------------------------------------------------
    // State machine
    // -------------------------------------------------------------------------

    enum GameState { PAUSED, COUNTDOWN, PLAYING }
    volatile GameState gameState = GameState.PAUSED;
    long countdownStartMs;

    // -------------------------------------------------------------------------
    // Game variables
    // -------------------------------------------------------------------------

    private int topPaddleX, bottomPaddleX;
    int ballX;
    int ballY;
    int ballVelX;
    int ballVelY;
    int topScore = 0;
    int bottomScore = 0;

    /** 0-based index into SPEED_VEL_X / SPEED_VEL_Y; shown in pause overlay as levels 1-5. */
    private int ballSpeedLevel = 1; // default = level 2

    /**
     * When true (default), each paddle hit normalizes the ball velocity back to
     * the magnitude set by {@code ballSpeedLevel}, preventing accumulated drift.
     * Toggled with the C key; displayed as a checkbox in the pause overlay.
     */
    private boolean constantSpeed = true;

    // -------------------------------------------------------------------------
    // Players
    // -------------------------------------------------------------------------

    PlayerVariant topVariant    = PlayerVariant.AI_HARD;
    private PlayerVariant bottomVariant = PlayerVariant.HUMAN;

    BasePlayer<PongState, PongAction> topPlayer;
    private BasePlayer<PongState, PongAction> bottomPlayer;
    private HumanPlayer<PongState, PongAction> activeHumanPlayer;

    /** Provided by Launcher; null = no hardware connected. */
    private final PongHardwareAI hardwarePlayer;

    // -------------------------------------------------------------------------
    // UIq
    // -------------------------------------------------------------------------

    private final Scoreboard  scoreboard;
    private final PongToolbar topToolbar;
    private final PongToolbar bottomToolbar;
    private final GameCanvas  canvas;

    // -------------------------------------------------------------------------
    // Game loop
    // -------------------------------------------------------------------------

    private volatile boolean running = false;
    private Thread gameThread;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * @param hardwarePlayer pre-wired hardware AI from the Launcher, or
     *                       {@code null} if no device is connected (grays out
     *                       the HARDWARE option in both toolbars).
     */
    public PongGame(PongHardwareAI hardwarePlayer) {
        this.hardwarePlayer = hardwarePlayer;
        boolean hwAvail = (hardwarePlayer != null);

        scoreboard = new Scoreboard();

        setLayout(new BorderLayout());
        setBackground(Color.BLACK);

        // Toolbars
        topToolbar    = new PongToolbar(PongToolbar.Side.TOP,    topVariant,    hwAvail, scoreboard);
        bottomToolbar = new PongToolbar(PongToolbar.Side.BOTTOM, bottomVariant, hwAvail, scoreboard);

        topToolbar.setLockedOutVariant(bottomVariant);
        bottomToolbar.setLockedOutVariant(topVariant);

        topToolbar.setOnVariantChanged(   v -> onVariantSelected(PongToolbar.Side.TOP,    v));
        bottomToolbar.setOnVariantChanged(v -> onVariantSelected(PongToolbar.Side.BOTTOM, v));

        // Canvas
        canvas = new GameCanvas();
        canvas.setPreferredSize(new Dimension(FIELD_WIDTH, FIELD_HEIGHT));
        canvas.setBackground(Color.BLACK);
        canvas.setFocusable(false);

        add(topToolbar,    BorderLayout.NORTH);
        add(canvas,        BorderLayout.CENTER);
        add(bottomToolbar, BorderLayout.SOUTH);

        // Key bindings (WHEN_IN_FOCUSED_WINDOW covers toolbar-focus edge case)
        setFocusable(true);
        bindGameKeys();

        // Initial players
        topPlayer    = createPlayer(topVariant);
        bottomPlayer = createPlayer(bottomVariant);
        wireHumanPlayer();

        resetBall();
        lockToolbars(false); // start PAUSED, toolbars unlocked
    }

    /**
     * Launcher-facing entry point that keeps Pong's hardware assembly inside
     * the Pong module instead of in {@code Launcher}.
     */
    public static JFrame launchFromLauncher(SerialConnectionManager pongManager) {
        PongGame game = new PongGame(createHardwarePlayer(pongManager));

        JFrame frame = new JFrame("Pong");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setResizable(false);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                game.stop();
            }
        });

        ConnectionStatusPanel pongStatus = new ConnectionStatusPanel();
        pongStatus.addDevice("Pong", pongManager);
        frame.add(pongStatus, BorderLayout.NORTH);
        frame.add(game, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        game.start();
        return frame;
    }

    static PongHardwareAI createHardwarePlayer(SerialConnectionManager pongManager) {
        if (pongManager == null || !pongManager.isConnected() || pongManager.getDeviceChannelCount() < 2) {
            return null;
        }

        NeuralSignalParser leftParser = new NeuralSignalParser(0);
        NeuralSignalParser rightParser = new NeuralSignalParser(1);
        HardwareSignalSource leftSource = new HardwareSignalSource(pongManager, leftParser);
        HardwareSignalSource rightSource = new HardwareSignalSource(pongManager, rightParser);
        return new PongHardwareAI("Hardware", leftSource, rightSource);
    }

    // =========================================================================
    // Key bindings
    // =========================================================================

    private void bindGameKeys() {
        InputMap  im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "game-esc");
        am.put("game-esc", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { togglePause(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0, false), "game-reset");
        am.put("game-reset", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { resetGame(); }
        });

        // C key: toggle constant-speed mode
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, false), "game-const-speed");
        am.put("game-const-speed", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                constantSpeed = !constantSpeed;
                canvas.repaint();
            }
        });

        // Speed keys 1-5: change ball speed level (takes effect on next ball reset)
        int[] speedKeys = {
            KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5
        };
        for (int i = 0; i < speedKeys.length; i++) {
            final int level = i;
            String id = "speed-" + (i + 1);
            im.put(KeyStroke.getKeyStroke(speedKeys[i], 0, false), id);
            am.put(id, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    ballSpeedLevel = level;
                    canvas.repaint(); // refresh speed indicator immediately in pause overlay
                }
            });
        }
    }

    /**
     * Wires LEFT/RIGHT arrow keys to the currently active HumanPlayer using
     * WHEN_IN_FOCUSED_WINDOW so toolbar focus does not break human control.
     */
    private void wireHumanPlayer() {
        unbindHumanKeys();
        activeHumanPlayer = null;

        HumanPlayer<PongState, PongAction> hp = null;
        if (topPlayer instanceof HumanPlayer<?, ?> h) {
            @SuppressWarnings("unchecked")
            HumanPlayer<PongState, PongAction> c = (HumanPlayer<PongState, PongAction>) h;
            hp = c;
        } else if (bottomPlayer instanceof HumanPlayer<?, ?> h) {
            @SuppressWarnings("unchecked")
            HumanPlayer<PongState, PongAction> c = (HumanPlayer<PongState, PongAction>) h;
            hp = c;
        }
        if (hp == null) return;

        activeHumanPlayer = hp;
        InputMap  im  = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am  = getActionMap();
        final HumanPlayer<PongState, PongAction> finalHp = hp;

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0, false), "hp-L-dn");
        am.put("hp-L-dn",  new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            finalHp.keyPressed(fakeKey(KeyEvent.VK_LEFT)); }});

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0, true),  "hp-L-up");
        am.put("hp-L-up",  new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            finalHp.keyReleased(fakeKey(KeyEvent.VK_LEFT)); }});

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "hp-R-dn");
        am.put("hp-R-dn",  new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            finalHp.keyPressed(fakeKey(KeyEvent.VK_RIGHT)); }});

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true),  "hp-R-up");
        am.put("hp-R-up",  new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {
            finalHp.keyReleased(fakeKey(KeyEvent.VK_RIGHT)); }});
    }

    private void unbindHumanKeys() {
        InputMap  im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        for (String k : new String[]{"hp-L-dn","hp-L-up","hp-R-dn","hp-R-up"}) am.remove(k);
        im.remove(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0, false));
        im.remove(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0, true));
        im.remove(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false));
        im.remove(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true));
    }

    private KeyEvent fakeKey(int code) {
        return new KeyEvent(this, KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
    }

    // =========================================================================
    // Player factory
    // =========================================================================

    /**
     * Instantiates the correct player for the given variant.
     *
     * <p>AI presets (parameters here for easy tuning):
     * <ul>
     *   <li>AI Easy - deadZone=22 px, reactionProb=0.72 (~28% tick skip)</li>
     *   <li>AI Hard - deadZone=8 px,  reactionProb=1.00 (perfect reaction)</li>
     * </ul>
     */
    private BasePlayer<PongState, PongAction> createPlayer(PlayerVariant variant) {
        return switch (variant) {
            case HUMAN    -> buildHumanPlayer();
            case HARDWARE -> hardwarePlayer;
            case AI_HARD  -> new PongSoftwareAI("AI Hard",  8, 1.00);
            case AI_EASY  -> new PongSoftwareAI("AI Easy", 22, 0.72);
        };
    }

    private HumanPlayer<PongState, PongAction> buildHumanPlayer() {
        return new HumanPlayer<>("Human",
                Map.of(KeyEvent.VK_LEFT, PongAction.LEFT,
                       KeyEvent.VK_RIGHT, PongAction.RIGHT),
                PongAction.IDLE);
    }

    // =========================================================================
    // Toolbar callbacks
    // =========================================================================

    void onVariantSelected(PongToolbar.Side side, PlayerVariant chosen) {
        PlayerVariant other = (side == PongToolbar.Side.TOP) ? bottomVariant : topVariant;
        if (chosen == other) return;

        if (side == PongToolbar.Side.TOP) {
            topVariant = chosen;
            bottomToolbar.setLockedOutVariant(topVariant);
        } else {
            bottomVariant = chosen;
            topToolbar.setLockedOutVariant(bottomVariant);
        }

        topPlayer    = createPlayer(topVariant);
        bottomPlayer = createPlayer(bottomVariant);
        wireHumanPlayer();
        resetBall();
        // Stay in PAUSED so the user can review before pressing ESC
    }

    // =========================================================================
    // Game loop
    // =========================================================================

    /** Starts the daemon game-loop thread and requests keyboard focus. */
    public void start() {
        running    = true;
        gameThread = new Thread(this::gameLoop, "pong-loop");
        gameThread.setDaemon(true);
        gameThread.start();
        requestFocusInWindow();
    }

    public void stop() { running = false; }

    private void gameLoop() {
        while (running) {
            long now = System.currentTimeMillis();
            switch (gameState) {
                case COUNTDOWN -> tickCountdown(now);
                case PLAYING   -> tickPlaying();
                case PAUSED    -> { /* idle */ }
            }
            canvas.repaint();
            try { Thread.sleep(16); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void tickCountdown(long now) {
        if (now - countdownStartMs >= COUNTDOWN_MS) gameState = GameState.PLAYING;
    }

    void tickPlaying() {
        // State snapshots
        PongState topState    = new PongState(topPaddleX,    ballX, ballY, FIELD_WIDTH);
        PongState bottomState = new PongState(bottomPaddleX, ballX, ballY, FIELD_WIDTH);

        // Actions
        PongAction topAct    = topPlayer.getNextMove(topState);
        PongAction bottomAct = bottomPlayer.getNextMove(bottomState);

        // Move paddles
        topPaddleX    = clampPaddle(topPaddleX    + dx(topAct));
        bottomPaddleX = clampPaddle(bottomPaddleX + dx(bottomAct));

        // Move ball
        ballX += ballVelX;
        ballY += ballVelY;

        // Left / right wall bounce
        if (ballX <= 0) {
            ballX = 0; ballVelX = Math.abs(ballVelX);
        } else if (ballX + BALL_SIZE >= FIELD_WIDTH) {
            ballX = FIELD_WIDTH - BALL_SIZE; ballVelX = -Math.abs(ballVelX);
        }

        // Top paddle collision (ball moving up: VelY < 0)
        if (ballVelY < 0
                && ballY <= TOP_PADDLE_Y + PADDLE_HEIGHT
                && ballY + BALL_SIZE >= TOP_PADDLE_Y
                && ballX + BALL_SIZE >= topPaddleX
                && ballX <= topPaddleX + PADDLE_WIDTH) {
            ballVelY = Math.abs(ballVelY);
            ballVelX += deflect(ballX, topPaddleX);
            maybeNormalizeSpeed();
            ballY = TOP_PADDLE_Y + PADDLE_HEIGHT + 1;
        }

        // Bottom paddle collision (ball moving down: VelY > 0)
        if (ballVelY > 0
                && ballY + BALL_SIZE >= BOTTOM_PADDLE_Y
                && ballY <= BOTTOM_PADDLE_Y + PADDLE_HEIGHT
                && ballX + BALL_SIZE >= bottomPaddleX
                && ballX <= bottomPaddleX + PADDLE_WIDTH) {
            ballVelY = -Math.abs(ballVelY);
            ballVelX += deflect(ballX, bottomPaddleX);
            maybeNormalizeSpeed();
            ballY = BOTTOM_PADDLE_Y - BALL_SIZE - 1;
        }

        // Ball exits top -> bottom scores
        if (ballY + BALL_SIZE < 0) {
            bottomScore++;
            scoreboard.recordPoint(bottomVariant, topVariant);
            startCountdown(); return;
        }

        // Ball exits bottom -> top scores
        if (ballY > FIELD_HEIGHT) {
            topScore++;
            scoreboard.recordPoint(topVariant, bottomVariant);
            startCountdown();
        }
    }

    // =========================================================================
    // State transitions
    // =========================================================================

    void togglePause() {
        switch (gameState) {
            case PAUSED    -> startCountdown();
            case PLAYING,
                 COUNTDOWN -> { gameState = GameState.PAUSED; lockToolbars(false); }
        }
    }

    private void resetGame() {
        topScore = 0; bottomScore = 0;
        gameState = GameState.PAUSED;
        lockToolbars(false);
        resetBall();
    }

    void startCountdown() {
        gameState        = GameState.COUNTDOWN;
        countdownStartMs = System.currentTimeMillis();
        lockToolbars(true);
        resetBall();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static int dx(PongAction a) {
        return switch (a) { case LEFT -> -PADDLE_SPEED; case RIGHT -> PADDLE_SPEED; case IDLE -> 0; };
    }

    private static int clampPaddle(int x) {
        return Math.max(0, Math.min(FIELD_WIDTH - PADDLE_WIDTH, x));
    }

    /**
     * Small X deflection based on where the ball hits the paddle center.
     * Hitting the edge imparts spin; dead center has no effect.
     */
    private static int deflect(int bx, int px) {
        int off = (bx + BALL_SIZE / 2) - (px + PADDLE_WIDTH / 2);
        return Math.max(-3, Math.min(3, off / 10));
    }

    /**
     * If constant-speed mode is on, scales the ball velocity back to exactly the
     * magnitude corresponding to the current {@code ballSpeedLevel}.  This is
     * called after every paddle hit so deflect-induced drift cannot accumulate.
     */
    private void maybeNormalizeSpeed() {
        if (!constantSpeed) return;
        double target  = Math.sqrt(
                (double) SPEED_VEL_X[ballSpeedLevel] * SPEED_VEL_X[ballSpeedLevel]
              + (double) SPEED_VEL_Y[ballSpeedLevel] * SPEED_VEL_Y[ballSpeedLevel]);
        double current = Math.sqrt((double) ballVelX * ballVelX + (double) ballVelY * ballVelY);
        if (current == 0) return;
        double scale = target / current;
        ballVelX = (int) Math.round(ballVelX * scale);
        ballVelY = (int) Math.round(ballVelY * scale);
        // Ensure Y always keeps non-zero direction so the ball never gets stuck
        if (ballVelY == 0) ballVelY = (ballVelY >= 0 ? 1 : -1);
    }

    private void resetBall() {
        ballX    = FIELD_WIDTH  / 2 - BALL_SIZE / 2;
        ballY    = FIELD_HEIGHT / 2 - BALL_SIZE / 2;
        ballVelX = (Math.random() > 0.5 ? 1 : -1) * SPEED_VEL_X[ballSpeedLevel];
        ballVelY = (Math.random() > 0.5 ? 1 : -1) * SPEED_VEL_Y[ballSpeedLevel];
        topPaddleX    = FIELD_WIDTH / 2 - PADDLE_WIDTH / 2;
        bottomPaddleX = FIELD_WIDTH / 2 - PADDLE_WIDTH / 2;
    }

    private void lockToolbars(boolean lock) {
        SwingUtilities.invokeLater(() -> {
            topToolbar.setSelectionLocked(lock);
            bottomToolbar.setSelectionLocked(lock);
        });
    }

    // =========================================================================
    // Game canvas (inner class - all painting)
    // =========================================================================

    private class GameCanvas extends JPanel {

        // Hit-test rectangles populated during paintPaused; used by the mouse listener.
        private final Rectangle[] speedRects  = new Rectangle[5];
        private Rectangle checkboxRect  = null;
        private Rectangle resumeRect    = null;
        private Rectangle resetRect     = null;

        // Current mouse position, used for hover highlighting in the pause overlay.
        private int mouseX = -1, mouseY = -1;

        GameCanvas() {
            // Click handler
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (gameState != GameState.PAUSED) return;
                    int mx = e.getX(), my = e.getY();

                    // Speed buttons 1-5
                    for (int i = 0; i < speedRects.length; i++) {
                        if (speedRects[i] != null && speedRects[i].contains(mx, my)) {
                            ballSpeedLevel = i;
                            repaint();
                            return;
                        }
                    }
                    // Constant-speed checkbox (click anywhere on the row)
                    if (checkboxRect != null && checkboxRect.contains(mx, my)) {
                        constantSpeed = !constantSpeed;
                        repaint();
                        return;
                    }
                    // Resume button
                    if (resumeRect != null && resumeRect.contains(mx, my)) {
                        togglePause();
                        return;
                    }
                    // Reset button
                    if (resetRect != null && resetRect.contains(mx, my)) {
                        resetGame();
                    }
                }
            });

            // Motion handler for hover highlight
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (gameState != GameState.PAUSED) return;
                    mouseX = e.getX();
                    mouseY = e.getY();
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);

            // Horizontal centre-line dashes
            g.setColor(new Color(55, 55, 55));
            for (int x = 0; x < FIELD_WIDTH; x += 30) g.fillRect(x, FIELD_HEIGHT / 2 - 2, 18, 4);

            // Paddles
            g.setColor(Color.WHITE);
            g2.fillRoundRect(topPaddleX,    TOP_PADDLE_Y,    PADDLE_WIDTH, PADDLE_HEIGHT, 8, 8);
            g2.fillRoundRect(bottomPaddleX, BOTTOM_PADDLE_Y, PADDLE_WIDTH, PADDLE_HEIGHT, 8, 8);

            // Ball
            g2.fillOval(ballX, ballY, BALL_SIZE, BALL_SIZE);

            // Scores
            g.setFont(new Font("Monospaced", Font.BOLD, 28));
            FontMetrics fm = g.getFontMetrics();
            String ts = String.valueOf(topScore);
            String bs = String.valueOf(bottomScore);
            int cx = FIELD_WIDTH / 2;
            g.setColor(new Color(200, 200, 200));
            g.drawString(ts, cx - fm.stringWidth(ts) / 2, FIELD_HEIGHT / 2 - 20);
            g.drawString(bs, cx - fm.stringWidth(bs) / 2, FIELD_HEIGHT / 2 + fm.getAscent() + 4);

            // Player name labels
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(120, 120, 120));
            g.drawString(topPlayer.getName(),    8, TOP_PADDLE_Y    + PADDLE_HEIGHT + 14);
            g.drawString(bottomPlayer.getName(), 8, BOTTOM_PADDLE_Y - 4);

            // State overlay
            switch (gameState) {
                case COUNTDOWN -> paintCountdown(g);
                case PAUSED    -> paintPaused(g);
                case PLAYING   -> { /* no overlay */ }
            }
        }

        private void paintCountdown(Graphics g) {
            long rem  = COUNTDOWN_MS - (System.currentTimeMillis() - countdownStartMs);
            int  dig  = (int) Math.ceil(rem / 1000.0);
            String tx = dig >= 1 ? String.valueOf(dig) : "GO!";

            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, FIELD_WIDTH, FIELD_HEIGHT);

            g.setFont(new Font("Monospaced", Font.BOLD, 80));
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(tx,
                    FIELD_WIDTH  / 2 - fm.stringWidth(tx) / 2,
                    FIELD_HEIGHT / 2 + fm.getAscent() / 2 - 8);
        }

        /**
         * Draws the pause overlay.  All clickable element bounds are stored in the
         * corresponding Rectangle fields so the MouseListener can hit-test them.
         */
        private void paintPaused(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(0, 0, FIELD_WIDTH, FIELD_HEIGHT);

            // Title
            g.setFont(new Font("Monospaced", Font.BOLD, 52));
            g.setColor(new Color(255, 220, 50));
            FontMetrics fm = g.getFontMetrics();
            String title = "PAUSED";
            g.drawString(title,
                    FIELD_WIDTH / 2 - fm.stringWidth(title) / 2,
                    FIELD_HEIGHT / 2 - 46);

            // Ball speed selector
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            fm = g.getFontMetrics();
            String speedLabel = "BALL SPEED:";
            int labelW = fm.stringWidth(speedLabel);
            int cellW  = 30;
            int gap    = 6;
            int totalSpeedW = labelW + gap + 5 * cellW + 4 * gap;
            int sx = FIELD_WIDTH / 2 - totalSpeedW / 2;
            int sy = FIELD_HEIGHT / 2 - 4;

            g.setColor(new Color(200, 200, 200));
            g.drawString(speedLabel, sx, sy + fm.getAscent());
            int bx = sx + labelW + gap;
            for (int i = 0; i < 5; i++) {
                boolean active = (i == ballSpeedLevel);
                int rx = bx + i * (cellW + gap);
                speedRects[i] = new Rectangle(rx, sy, cellW, cellW);
                boolean hovered = speedRects[i].contains(mouseX, mouseY);

                if (active) {
                    g.setColor(new Color(255, 200, 0));
                    g2.fillRoundRect(rx, sy, cellW, cellW, 6, 6);
                    g.setColor(Color.BLACK);
                } else if (hovered) {
                    g.setColor(new Color(120, 100, 0));
                    g2.fillRoundRect(rx, sy, cellW, cellW, 6, 6);
                    g.setColor(new Color(255, 220, 100));
                } else {
                    g.setColor(new Color(80, 80, 80));
                    g2.fillRoundRect(rx, sy, cellW, cellW, 6, 6);
                    g.setColor(new Color(180, 180, 180));
                }
                String num = String.valueOf(i + 1);
                g.setFont(new Font("Monospaced", Font.BOLD, 14));
                fm = g.getFontMetrics();
                g.drawString(num,
                        rx + cellW / 2 - fm.stringWidth(num) / 2,
                        sy + cellW / 2 + fm.getAscent() / 2 - 2);
            }

            // Constant-speed checkbox
            int cby  = FIELD_HEIGHT / 2 + 36;
            int cbSz = 16;
            int cbX  = FIELD_WIDTH / 2 - 84;

            // Extend the hit area to include the label text
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            fm = g.getFontMetrics();
            String cbLabel = "Constant Speed  (C)";
            int cbRowW = cbSz + 8 + fm.stringWidth(cbLabel);
            checkboxRect = new Rectangle(cbX, cby, cbRowW, cbSz + 4);
            boolean cbHovered = checkboxRect.contains(mouseX, mouseY);

            g2.setStroke(new BasicStroke(2f));
            g.setColor(constantSpeed
                    ? new Color(255, 200, 0)
                    : (cbHovered ? new Color(160, 140, 60) : new Color(100, 100, 100)));
            g2.drawRoundRect(cbX, cby, cbSz, cbSz, 4, 4);
            if (constantSpeed) {
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(255, 200, 0));
                g2.drawLine(cbX + 3, cby + 8,  cbX + 6,  cby + 12);
                g2.drawLine(cbX + 6, cby + 12, cbX + 13, cby + 4);
            }
            g2.setStroke(new BasicStroke(1f));
            g.setColor(cbHovered ? new Color(255, 240, 160) : new Color(210, 210, 210));
            g.drawString(cbLabel, cbX + cbSz + 8, cby + fm.getAscent() - 1);

            // ---- Resume / Reset buttons --------------------------------------
            int btnY  = FIELD_HEIGHT / 2 + 64;
            int btnH  = 26;
            int btnW  = 110;
            int btnGap = 16;
            int resumeX = FIELD_WIDTH / 2 - btnW - btnGap / 2;
            int resetX  = FIELD_WIDTH / 2 + btnGap / 2;

            resumeRect = new Rectangle(resumeX, btnY, btnW, btnH);
            resetRect  = new Rectangle(resetX,  btnY, btnW, btnH);

            paintButton(g2, resumeRect, "Resume  (ESC)", resumeRect.contains(mouseX, mouseY),
                        new Color(40, 130, 60), new Color(60, 180, 80));
            paintButton(g2, resetRect,  "Reset  (R)",   resetRect.contains(mouseX, mouseY),
                        new Color(130, 50, 40), new Color(190, 70, 55));

            // ---- Keyboard hint (small, dim) ----------------------------------
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(90, 90, 90));
            fm = g.getFontMetrics();
            String hint = "1-5 speed  \u2022  C constant speed  \u2022  ESC resume  \u2022  R reset";
            g.drawString(hint,
                    FIELD_WIDTH / 2 - fm.stringWidth(hint) / 2,
                    FIELD_HEIGHT / 2 + 106);
        }

        /** Draws a labelled button rectangle with hover tinting. */
        private void paintButton(Graphics2D g2, Rectangle r,
                                 String label, boolean hovered,
                                 Color baseColor, Color hoverColor) {
            g2.setColor(hovered ? hoverColor : baseColor);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);

            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(hovered ? new Color(220, 255, 220) : new Color(160, 200, 160));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g2.setStroke(new BasicStroke(1f));

            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Color.WHITE);
            g2.drawString(label,
                    r.x + r.width  / 2 - fm.stringWidth(label) / 2,
                    r.y + r.height / 2 + fm.getAscent() / 2 - 2);
        }
    }

    // =========================================================================
    // Standalone entry point (software-only, no hardware)
    // =========================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PongGame game = new PongGame(null);
            JFrame frame = new JFrame("Pong");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.start();
        });
    }
}
