package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.annotations.GeneratedExcludeFromCoverage;
import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import edu.sjsu.spring2026.group32.launcher.ui.ConnectionStatusPanel;
import edu.sjsu.spring2026.group32.pong.PongGame;
import edu.sjsu.spring2026.group32.pong.ai.PongHardwareAI;
import edu.sjsu.spring2026.group32.pong.core.PongHardwareFactory;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Window assembly for Pong.
 */
@GeneratedExcludeFromCoverage
public final class PongFrame {
    private PongFrame() {
    }

    public static JFrame launchFromLauncher(SerialConnectionManager pongManager) {
        PongHardwareAI hardwarePlayer = PongHardwareFactory.createHardwarePlayer(pongManager);
        PongGame game = new PongGame(hardwarePlayer, pongManager);
        JFrame frame = buildFrame(game, pongManager, hardwarePlayer);
        frame.setVisible(true);
        game.start();
        return frame;
    }

    public static void launchStandalone() {
        PongGame game = new PongGame(null, null);
        JFrame frame = buildFrame(game, null, null);
        frame.setVisible(true);
        game.start();
    }

    private static JFrame buildFrame(PongGame game,
                                     SerialConnectionManager pongManager,
                                     PongHardwareAI hardwarePlayer) {
        JFrame frame = new JFrame("Pong");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setResizable(false);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                game.stop();
                game.shutdownHardwareListener();
                game.stopHardwareInjection();
            }
        });

        if (hardwarePlayer != null) {
            Runtime.getRuntime().addShutdownHook(new Thread(hardwarePlayer::close));
        }

        if (pongManager != null) {
            ConnectionStatusPanel pongStatus = new ConnectionStatusPanel();
            pongStatus.addDevice("Pong", pongManager);
            frame.add(pongStatus, BorderLayout.NORTH);
        }

        frame.add(game, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        return frame;
    }
}
