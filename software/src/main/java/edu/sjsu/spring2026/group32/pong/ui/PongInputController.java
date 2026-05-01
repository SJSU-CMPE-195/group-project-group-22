package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.player.HumanPlayer;
import edu.sjsu.spring2026.group32.pong.model.PongAction;
import edu.sjsu.spring2026.group32.pong.model.PongState;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.IntConsumer;

/**
 * Owns Swing key bindings for Pong.
 */
public class PongInputController {
    public void bindGameKeys(JComponent component,
                             Runnable onTogglePause,
                             Runnable onReset,
                             Runnable onToggleConstantSpeed,
                             IntConsumer onSpeedSelected,
                             Runnable onRefresh) {
        InputMap inputMap = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = component.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "game-esc");
        actionMap.put("game-esc", action(onTogglePause));

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0, false), "game-reset");
        actionMap.put("game-reset", action(onReset));

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, false), "game-const-speed");
        actionMap.put("game-const-speed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onToggleConstantSpeed.run();
                onRefresh.run();
            }
        });

        int[] speedKeys = {
                KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5
        };
        for (int i = 0; i < speedKeys.length; i++) {
            final int level = i;
            String id = "speed-" + (i + 1);
            inputMap.put(KeyStroke.getKeyStroke(speedKeys[i], 0, false), id);
            actionMap.put(id, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    onSpeedSelected.accept(level);
                    onRefresh.run();
                }
            });
        }
    }

    public void wireHumanPlayer(JComponent component, HumanPlayer<PongState, PongAction> humanPlayer) {
        unbindHumanKeys(component);
        if (humanPlayer == null) {
            return;
        }

        InputMap inputMap = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = component.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "hp-L-dn");
        actionMap.put("hp-L-dn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                humanPlayer.keyPressed(fakeKey(component, KeyEvent.VK_LEFT));
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true), "hp-L-up");
        actionMap.put("hp-L-up", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                humanPlayer.keyReleased(fakeKey(component, KeyEvent.VK_LEFT));
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "hp-R-dn");
        actionMap.put("hp-R-dn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                humanPlayer.keyPressed(fakeKey(component, KeyEvent.VK_RIGHT));
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true), "hp-R-up");
        actionMap.put("hp-R-up", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                humanPlayer.keyReleased(fakeKey(component, KeyEvent.VK_RIGHT));
            }
        });
    }

    public void unbindHumanKeys(JComponent component) {
        InputMap inputMap = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = component.getActionMap();
        for (String key : new String[]{"hp-L-dn", "hp-L-up", "hp-R-dn", "hp-R-up"}) {
            actionMap.remove(key);
        }
        inputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false));
        inputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true));
        inputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false));
        inputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true));
    }

    private AbstractAction action(Runnable runnable) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runnable.run();
            }
        };
    }

    private KeyEvent fakeKey(JComponent component, int code) {
        return new KeyEvent(
                component,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                code,
                KeyEvent.CHAR_UNDEFINED);
    }
}
