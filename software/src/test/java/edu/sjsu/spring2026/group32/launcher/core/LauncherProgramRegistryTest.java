package edu.sjsu.spring2026.group32.launcher.core;

import edu.sjsu.spring2026.group32.launcher.model.LauncherProgram;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Window;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;

@DisplayName("LauncherProgramRegistry Suite")
class LauncherProgramRegistryTest {

    private LauncherProgramRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new LauncherProgramRegistry();
    }

    // register / isOpen

    @Test
    @DisplayName("program is not open before registration")
    void registerAndWindowClose_coverPositiveAndNegativePaths_notOpenBeforeRegister() {
        assertFalse(registry.isOpen(LauncherProgram.HIT_THE_ZONE), "program should not be open before registration");
    }

    @Test
    @DisplayName("program is open immediately after registration")
    void registerAndWindowClose_coverPositiveAndNegativePaths_openAfterRegister() {
        registry.register(LauncherProgram.HIT_THE_ZONE, new Window(null), null);

        assertTrue(registry.isOpen(LauncherProgram.HIT_THE_ZONE), "program should be open after registration");
    }

    @Test
    @DisplayName("register() returns the same window instance it was given")
    void registerAndWindowClose_coverPositiveAndNegativePaths_returnsWindow() {
        Window window   = new Window(null);
        Window returned = registry.register(LauncherProgram.HIT_THE_ZONE, window, null);

        assertSame(window, returned, "register() should return the same window instance");
    }

    @Test
    @DisplayName("multiple programs can be open independently")
    void registerAndWindowClose_coverPositiveAndNegativePaths_multiplePrograms() {
        registry.register(LauncherProgram.HIT_THE_ZONE, new Window(null), null);
        registry.register(LauncherProgram.PONG, new Window(null), null);

        assertTrue(registry.isOpen(LauncherProgram.HIT_THE_ZONE), "HTZ should be open");
        assertTrue(registry.isOpen(LauncherProgram.PONG), "Pong should be open");
    }

    // windowClosed — removal and callback

    @Test
    @DisplayName("program is removed from registry when window closes")
    void registerAndWindowClose_coverPositiveAndNegativePaths_removedOnClose() {
        Window window = new Window(null);
        registry.register(LauncherProgram.HIT_THE_ZONE, window, null);

        fireWindowClosed(window);

        assertFalse(registry.isOpen(LauncherProgram.HIT_THE_ZONE), "program should no longer be open after window closes");
    }

    @Test
    @DisplayName("onClosed callback is invoked when window closes")
    void registerAndWindowClose_coverPositiveAndNegativePaths_callbackInvoked() {
        AtomicInteger callCount = new AtomicInteger(0);
        Window window = new Window(null);

        registry.register(LauncherProgram.HIT_THE_ZONE, window, callCount::incrementAndGet);

        fireWindowClosed(window);
        assertEquals(1, callCount.get(), "onClosed callback should be invoked exactly once");
    }

    @Test
    @DisplayName("null onClosed callback does not throw when window closes")
    void registerAndWindowClose_coverPositiveAndNegativePaths_nullCallbackSafe() {
        Window window = new Window(null);
        registry.register(LauncherProgram.HIT_THE_ZONE, window, null);

        fireWindowClosed(window); // should not throw

        assertFalse(registry.isOpen(LauncherProgram.HIT_THE_ZONE), "program should be removed even with null callback");
    }

    @Test
    @DisplayName("closing one program does not affect another")
    void registerAndWindowClose_coverPositiveAndNegativePaths_closingOneDoesNotAffectOther() {
        Window htzWindow  = new Window(null);
        Window pongWindow = new Window(null);

        registry.register(LauncherProgram.HIT_THE_ZONE, htzWindow,  null);
        registry.register(LauncherProgram.PONG, pongWindow, null);

        fireWindowClosed(htzWindow);

        assertFalse(registry.isOpen(LauncherProgram.HIT_THE_ZONE), "HTZ should be closed");
        assertTrue(registry.isOpen(LauncherProgram.PONG), "Pong should still be open");
    }

    @Test
    @DisplayName("re-registering a program after close makes it open again")
    void registerAndWindowClose_coverPositiveAndNegativePaths_reregisterAfterClose() {
        Window first = new Window(null);
        registry.register(LauncherProgram.HIT_THE_ZONE, first, null);
        fireWindowClosed(first);

        assertFalse(registry.isOpen(LauncherProgram.HIT_THE_ZONE));

        registry.register(LauncherProgram.HIT_THE_ZONE, new Window(null), null);

        assertTrue(registry.isOpen(LauncherProgram.HIT_THE_ZONE), "re-registering after close should make program open again");
    }

    // helper to bypass WindowEvent construction by calling listeners directly 

    private static void fireWindowClosed(Window window) {
        for (var listener : window.getWindowListeners()) {
            listener.windowClosed(null);
        }
    }
}