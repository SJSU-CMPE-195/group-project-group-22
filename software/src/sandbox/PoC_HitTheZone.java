package sandbox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class PoC_HitTheZone extends JFrame {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 300;
    private static final int BALL_DIAMETER = 20;
    private static final int START_X = 40;
    private static final int TRACK_Y = 120;
    private static final int SPEED = 5;

    private static final int ZONE_WIDTH = 80;
    private static final int ZONE_START = (WIDTH - ZONE_WIDTH) / 2;

    private int ballX = START_X;
    private int direction = +SPEED;

    // Scoring & Tracking
    private int successfulHits = 0;
    private int totalAttempts = 0;
    private int totalPasses = 0;

    private boolean inZone = false;
    private boolean canScore = false;
    private boolean isPaused = false;

    // ---- Swing ----
    private final JLabel topLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel xyLabel = new JLabel("", SwingConstants.CENTER);
    private final JButton scoreButton = new JButton("Score (Space)");
    private final JButton pauseButton = new JButton("Pause (Esc)");
    private final JButton resetButton = new JButton("Reset (R)");
    private final GamePanel gamePanel = new GamePanel();

    private final Timer tick;

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

    private void setupKeyBindings() {
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

    private void togglePause() {
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

    private void resetGame() {
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

    private void onTick() {
        if (isPaused) return;

        // Move
        ballX += direction;

        // Bounds (use panel width to be safe)
        int rightBound = gamePanel.getWidth() - BALL_DIAMETER;
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

    private void attemptScore() {
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

    private void updateHud() {
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
    private class GamePanel extends JPanel {
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