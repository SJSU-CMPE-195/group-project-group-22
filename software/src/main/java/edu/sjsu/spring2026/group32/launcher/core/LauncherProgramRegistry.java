package edu.sjsu.spring2026.group32.launcher.core;

import edu.sjsu.spring2026.group32.launcher.model.LauncherProgram;

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.Map;

/**
 * Tracks which launcher-managed program windows are currently open.
 */
public class LauncherProgramRegistry {
    private final Map<LauncherProgram, Window> openWindows = new EnumMap<>(LauncherProgram.class);

    public boolean isOpen(LauncherProgram program) {
        return openWindows.containsKey(program);
    }

    public <T extends Window> T register(LauncherProgram program, T window, Runnable onClosed) {
        openWindows.put(program, window);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                openWindows.remove(program);
                if (onClosed != null) {
                    onClosed.run();
                }
            }
        });
        return window;
    }
}
