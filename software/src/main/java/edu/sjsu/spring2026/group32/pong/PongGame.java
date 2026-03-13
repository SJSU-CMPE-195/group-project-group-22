package edu.sjsu.spring2026.group32.pong;

import edu.sjsu.spring2026.group32.player.BasePlayer;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

public class PongGame extends JPanel implements Runnable {

    private final BasePlayer<PongState, PongAction> player1;
    private final BasePlayer<PongState, PongAction> player2;

    // Window dimensions
    private final int WIDTH = 800;
    private final int HEIGHT = 600;

    // Game state variables mapped to pixel coordinates
    private int p1Y = 250;
    private int p2Y = 250;
    private int ballX = 400;
    private int ballY = 300;

    private int ballVelocityX = -6; // Speed of the ball
    private int ballVelocityY = 4;
    private final int PADDLE_SPEED = 8;

    private boolean isRunning = true;

    public PongGame(BasePlayer<PongState, PongAction> player1, BasePlayer<PongState, PongAction> player2) {
        this.player1 = player1;
        this.player2 = player2;

        // Set up the UI Panel
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
    }

    public void start() {
        // Run the game loop in a new thread so it doesn't block the UI thread
        new Thread(this).start();
    }

    @Override
    public void run() {
        while (isRunning) {
            updateLogic();
            repaint(); // Triggers paintComponent()

            try {
                Thread.sleep(16); // ~60 FPS
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateLogic() {
        // 1. Generate state
        PongState stateP1 = new PongState(p1Y, ballX, ballY);
        PongState stateP2 = new PongState(p2Y, ballX, ballY);

        // 2. Poll actions
        PongAction p1Action = player1.getNextMove(stateP1);
        PongAction p2Action = player2.getNextMove(stateP2);

        // 3. Apply paddle movement with boundary checks
        if (p1Action == PongAction.UP) p1Y = Math.max(0, p1Y - PADDLE_SPEED);
        if (p1Action == PongAction.DOWN) p1Y = Math.min(HEIGHT - 100, p1Y + PADDLE_SPEED);

        if (p2Action == PongAction.UP) p2Y = Math.max(0, p2Y - PADDLE_SPEED);
        if (p2Action == PongAction.DOWN) p2Y = Math.min(HEIGHT - 100, p2Y + PADDLE_SPEED);

        // 4. Ball Movement
        ballX += ballVelocityX;
        ballY += ballVelocityY;

        // Top and bottom wall collisions
        if (ballY <= 0 || ballY >= HEIGHT - 15) {
            ballVelocityY *= -1;
        }

        // Paddle collisions
        // Player 1 (Left Paddle at X=30, Width=20, Height=100)
        if (ballX <= 50 && ballY + 15 >= p1Y && ballY <= p1Y + 100) {
            ballVelocityX = Math.abs(ballVelocityX); // Force right
        }
        // Player 2 (Right Paddle at X=750, Width=20, Height=100)
        if (ballX >= 735 && ballY + 15 >= p2Y && ballY <= p2Y + 100) {
            ballVelocityX = -Math.abs(ballVelocityX); // Force left
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // Clears the screen with the background color
        g.setColor(Color.WHITE);

        // Draw Player 1
        g.fillRect(30, p1Y, 20, 100);

        // Draw Player 2
        g.fillRect(750, p2Y, 20, 100);

        // Draw Center Dashed Line
        for (int i = 0; i < HEIGHT; i += 30) {
            g.fillRect(WIDTH / 2 - 2, i, 4, 15);
        }

        // Draw Ball
        g.fillRect(ballX, ballY, 15, 15);
    }

    // --- Main Method ---
    public static void main(String[] args) {
        // 1. Create the players
        BasePlayer<PongState, PongAction> aiPlayer1 = new PongSoftwareAI("SoftwareAI1");
        BasePlayer<PongState, PongAction> aiPlayer2 = new PongSoftwareAI("SoftwareAI2");

        // 2. Create the game panel
        PongGame gamePanel = new PongGame(aiPlayer1, aiPlayer2);

        // 3. Set up the application window
        JFrame frame = new JFrame(String.format("Pong - %s vs %s", aiPlayer1.getName(), aiPlayer2.getName()));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(gamePanel);
        frame.pack(); // Sizes the frame to fit the preferred size of the gamePanel
        frame.setLocationRelativeTo(null); // Centers the window
        frame.setVisible(true);

        // 4. Start the game loop
        gamePanel.start();
    }
}