package edu.sjsu.spring2026.group32.sandbox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class PoC_HitTheZone extends JFrame {

    static final int WIDTH = 640;
    static final int HEIGHT = 300;
    static final int BALL_DIAMETER = 20;
    static final int START_X = 40;
    static final int TRACK_Y = 120;
    static final int SPEED = 5;

    static final int ZONE_WIDTH = 80;
    static final int ZONE_START = (WIDTH - ZONE_WIDTH) / 2;

    protected int ballX = START_X;
    protected int direction = +SPEED;

    // Scoring & Tracking
    protected int successfulHits = 0;
    protected int totalAttempts = 0;
    protected int totalPasses = 0;

    protected boolean inZone = false;
    protected boolean canScore = false;
    protected boolean isPaused = false;

    // ---- Swing ----
    protected final JLabel topLabel = new JLabel("", SwingConstants.CENTER);
    protected final JLabel xyLabel = new JLabel("", SwingConstants.CENTER);
    protected final JButton scoreButton = new JButton("Score (Space)");
    protected final JButton pauseButton = new JButton("Pause (Esc)");
    protected final JButton resetButton = new JButton("Reset (R)");
    protected final GamePanel gamePanel = new GamePanel();

    protected final Timer tick;

    // ---- Headless constructor for testing ----
    // In test env there isn't a screen to render so the GUI times out --> gives us a bare instance w/ game state + no bg timer
    protected PoC_HitTheZone(boolean headless) {
        super("Hit The Zone"); 
        tick = null; // time isn't needed for the tests so far
    } 

    public PoC_HitTheZone() {
        super("Hit The Zone");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        top.add(topLabel);
        top.add(xyLabel);
        add(top, BorderLayout.NORTH);

        gamePanel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        add(gamePanel, BorderLayout.CENTER);

        // Buttons
        scoreButton.setFocusable(false); // Prevents spacebar double-firing
        scoreButton.addActionListener(e -> attemptScore());

        pauseButton.setFocusable(false);
        pauseButton.addActionListener(e -> togglePause());

        resetButton.setFocusable(false);
        resetButton.addActionListener(e -> resetGame());

        JPanel bottom = new JPanel();
        bottom.add(scoreButton);
        bottom.add(pauseButton);
        bottom.add(resetButton);
        add(bottom, BorderLayout.SOUTH);

        setupKeyBindings();

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);

        updateHud();
        tick = new Timer(16, e -> onTick()); // ~60fps
        tick.start();
    }

    protected void setupKeyBindings() {
        InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = gamePanel.getActionMap();

        // Spacebar for scoring
        im.put(KeyStroke.getKeyStroke("SPACE"), "scoreAction");
        am.put("scoreAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                attemptScore();
            }
        });

        // Escape for pausing
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "pauseAction");
        am.put("pauseAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                togglePause();
            }
        });

        // R for resetting
        im.put(KeyStroke.getKeyStroke("R"), "resetAction");
        am.put("resetAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetGame();
            }
        });
    }

    protected void togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            pauseButton.setText("Resume (Esc)");
            scoreButton.setEnabled(false);
            topLabel.setText("--- PAUSED ---");
        } else {
            pauseButton.setText("Pause (Esc)");
            scoreButton.setEnabled(true);
            updateHud();
        }
    }

    protected void resetGame() {
        // Reset all game variables
        ballX = START_X;
        direction = +SPEED;
        successfulHits = 0;
        totalAttempts = 0;
        totalPasses = 0;
        inZone = false;
        canScore = false;

        // Unpause if it was paused
        if (isPaused) {
            togglePause();
        }

        updateHud();
        gamePanel.repaint();
    }

    protected void onTick() {
        if (isPaused) return;

        // Move
        ballX += direction;

        // For testing: fallback to width constant if panel hasn't rendered (in headless env)
        int panelWidth = gamePanel.getWidth() > 0 ? gamePanel.getWidth() : WIDTH;

        // Bounds (use panel width to be safe)
        int rightBound = panelWidth - BALL_DIAMETER;
        if (ballX <= 0) {
            ballX = 0;
            direction = +SPEED;
        } else if (ballX >= rightBound) {
            ballX = rightBound;
            direction = -SPEED;
        }

        int centerX = ballX + BALL_DIAMETER / 2;
        boolean nowInZone = centerX >= ZONE_START && centerX <= (ZONE_START + ZONE_WIDTH);

        // Reset scoring ability and count a pass when re-entering the zone
        if (!inZone && nowInZone) {
            canScore = true;
            totalPasses++;
        }

        inZone = nowInZone;

        updateHud();
        gamePanel.repaint();
    }

    protected void attemptScore() {
        if (isPaused) return;

        totalAttempts++;

        int centerX = ballX + BALL_DIAMETER / 2;
        boolean inside = centerX >= ZONE_START && centerX <= (ZONE_START + ZONE_WIDTH);

        if (inside && canScore) {
            successfulHits++;
            canScore = false; // Prevents getting multiple hits on a single pass
        }

        updateHud();
    }

    protected void updateHud() {
        if (isPaused) return;

        String accuracy = totalAttempts == 0 ? "0%" : String.format("%d%%", (successfulHits * 100) / totalAttempts);

        topLabel.setText(String.format(
                "Successful Hits: %d / Total Attempts: %d    |    Passes: %d    |    Accuracy: %s    |    %s",
                successfulHits, totalAttempts, totalPasses, accuracy,
                inZone ? "In Zone" : "Out of Zone"
        ));
        xyLabel.setText(String.format("Ball position: (x=%d, y=%d)", ballX, TRACK_Y));
    }

    // Drawing
    protected class GamePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Track
            g.setColor(Color.LIGHT_GRAY);
            int trackHeight = 40;
            g.fillRect(0, TRACK_Y - trackHeight / 2, getWidth(), trackHeight);

            // Zone
            g.setColor(new Color(255, 230, 128)); // soft yellow
            g.fillRect(ZONE_START, TRACK_Y - trackHeight / 2, ZONE_WIDTH, trackHeight);

            // Zone border
            g.setColor(Color.DARK_GRAY);
            g.drawRect(ZONE_START, TRACK_Y - trackHeight / 2, ZONE_WIDTH, trackHeight);

            // Ball
            g.setColor(Color.RED);
            g.fillOval(ballX, TRACK_Y - BALL_DIAMETER / 2, BALL_DIAMETER, BALL_DIAMETER);
            g.setColor(Color.BLACK);
            g.drawOval(ballX, TRACK_Y - BALL_DIAMETER / 2, BALL_DIAMETER, BALL_DIAMETER);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PoC_HitTheZone::new);
    }
}