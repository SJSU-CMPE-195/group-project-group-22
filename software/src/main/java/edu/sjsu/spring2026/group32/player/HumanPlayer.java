package edu.sjsu.spring2026.group32.player;

import edu.sjsu.spring2026.group32.player.model.Action;
import edu.sjsu.spring2026.group32.player.model.BasePlayer;
import edu.sjsu.spring2026.group32.player.model.GameState;
import edu.sjsu.spring2026.group32.player.model.PlayerType;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Map;


public class HumanPlayer<S extends GameState, A extends Action> implements BasePlayer<S, A>, KeyListener {
    private final String name;
    private final Map<Integer, A> keyBindings;
    private final A defaultAction;
    private A currentAction;

    public HumanPlayer(String name, Map<Integer, A> keyBindings, A defaultAction) {
        this.name = name;
        this.keyBindings = keyBindings;
        this.defaultAction = defaultAction;
        this.currentAction = defaultAction;
    }

    @Override
    public A getNextMove(S state) {
        return currentAction;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (keyBindings.containsKey(e.getKeyCode())) {
            currentAction = keyBindings.get(e.getKeyCode());
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (keyBindings.containsKey(e.getKeyCode())) {
            currentAction = defaultAction;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public String getName() { return this.name; }

    @Override
    public PlayerType getType() { return PlayerType.HUMAN; }
}
