package src;

import javax.swing.*;
import java.awt.*;

// PoC (proof of concept) game (simpler than pong)
// Requires fewer neurons


public class BallGame extends JFrame {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 300;
    private static final int BALL_DIAMETER = 20;
    private static final int START_X = 40;
    private static final int TRACK_Y = 120;
    private static final int SPEED = 5;
    private static final int TIME_LIMIT_SECONDS = 30;
    private static final int MAX_SCORE = 10;


    private static final int ZONE_WIDTH = 80;
    private static final int ZONE_START = (WIDTH - ZONE_WIDTH) / 2;


    private int ballX = START_X;
    private int direction = +SPEED;
    private int score = 0;

    private boolean inZone = false;
    private boolean canScore = false;

    private long startNanos;
    private boolean gameOver = false;
    private String gameOverReason = "";

    // ---- Swing ----
    private final JLabel topLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel xyLabel = new JLabel("", SwingConstants.CENTER);
    private final JButton scoreButton = new JButton("Click for Point");
    private final GamePanel gamePanel = new GamePanel();

    private final Timer tick;

    public BallGame() {
        super("Ball Game");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());


        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        top.add(topLabel);
        top.add(xyLabel);
        add(top, BorderLayout.NORTH);


        gamePanel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        add(gamePanel, BorderLayout.CENTER);


        scoreButton.addActionListener(e -> attemptScore());
        JPanel bottom = new JPanel();
        bottom.add(scoreButton);
        add(bottom, BorderLayout.SOUTH);

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);


        startNanos = System.nanoTime();
        updateHud(0);
        tick = new Timer(16, e -> onTick()); // ~60fps
        tick.start();
    }

    private void onTick() {
        if (gameOver) return;

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


        if (!inZone && nowInZone) {
            canScore = true;
        }


        if (inZone && !nowInZone && canScore) {
            endGame("You missed the zone on that pass.");
        }

        inZone = nowInZone;


        long elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000L;
        long remaining = Math.max(0, TIME_LIMIT_SECONDS - elapsedSec);


        if (remaining == 0) {
            endGame("Time’s up!");
        }


        updateHud(remaining);
        gamePanel.repaint();
    }

    private void attemptScore() {
        if (gameOver) return;

        int centerX = ballX + BALL_DIAMETER / 2;
        boolean inside =
                centerX >= ZONE_START && centerX <= (ZONE_START + ZONE_WIDTH);

        if (inside && canScore) {
            score++;
            canScore = false;
            if (score >= MAX_SCORE) {
                endGame("You reached the max score! 🎉");
            }
        }

        long elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000L;
        updateHud(Math.max(0, TIME_LIMIT_SECONDS - elapsedSec));
    }

    private void endGame(String reason) {
        gameOver = true;
        gameOverReason = reason;
        scoreButton.setEnabled(false);
        tick.stop();
        updateHud(Math.max(0, TIME_LIMIT_SECONDS - ((System.nanoTime() - startNanos) / 1_000_000_000L)));
        JOptionPane.showMessageDialog(this,
                reason + "\nFinal Score: " + score,
                "Game Over",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateHud(long remainingSeconds) {
        topLabel.setText(String.format(
                "Score: %d / %d    |    Time Left: %ds    |    %s",
                score, MAX_SCORE, remainingSeconds,
                gameOver ? ("Game Over: " + gameOverReason) : (inZone ? "In Zone" : "Out of Zone")
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
        SwingUtilities.invokeLater(BallGame::new);
    }
}
