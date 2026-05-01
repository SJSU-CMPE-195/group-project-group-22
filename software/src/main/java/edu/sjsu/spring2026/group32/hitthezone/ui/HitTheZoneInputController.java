package edu.sjsu.spring2026.group32.hitthezone.ui;

import edu.sjsu.spring2026.group32.player.model.BasePlayer;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns key bindings and global human-player dispatch for Hit The Zone.
 */
public class HitTheZoneInputController {
    private final List<KeyEventDispatcher> dispatchers = new ArrayList<>();

    public void bindKeys(JComponent component, Runnable onPause, Runnable onReset) {
        InputMap inputMap = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = component.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "pauseAction");
        actionMap.put("pauseAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onPause.run();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("R"), "resetAction");
        actionMap.put("resetAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onReset.run();
            }
        });
    }

    public void registerHumanPlayers(List<? extends BasePlayer<?, ?>> players) {
        unregisterHumanPlayers();
        for (BasePlayer<?, ?> player : players) {
            if (player instanceof KeyListener keyListener) {
                KeyEventDispatcher dispatcher = event -> {
                    if (event.getID() == KeyEvent.KEY_PRESSED) {
                        keyListener.keyPressed(event);
                    }
                    if (event.getID() == KeyEvent.KEY_RELEASED) {
                        keyListener.keyReleased(event);
                    }
                    return false;
                };
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
                dispatchers.add(dispatcher);
            }
        }
    }

    public void unregisterHumanPlayers() {
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        for (KeyEventDispatcher dispatcher : dispatchers) {
            manager.removeKeyEventDispatcher(dispatcher);
        }
        dispatchers.clear();
    }
}
