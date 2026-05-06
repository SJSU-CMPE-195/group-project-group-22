package edu.sjsu.spring2026.group32.testsupport;

import org.mockito.Mockito;

import java.awt.Window;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Convenience Mockito helpers for tests that involve AWT components.
 *
 * <p>AWT classes such as {@link Window} require a display to instantiate. These
 * helpers create mocked equivalents that work in headless CI environments.</p>
 */
public final class MockitoAwtSupport {
    private MockitoAwtSupport() {
    }

    /**
     * Returns a Mockito mock of {@link Window} whose {@code addWindowListener} /
     * {@code getWindowListeners} methods are wired together so that listeners
     * added via {@code addWindowListener} are returned by {@code getWindowListeners}.
     *
     * <p>No AWT display is required.</p>
     */
    public static Window mockWindow() {
        Window window = Mockito.mock(Window.class);
        List<WindowListener> listeners = new ArrayList<>();

        doAnswer(invocation -> {
            listeners.add(invocation.getArgument(0));
            return null;
        }).when(window).addWindowListener(any(WindowListener.class));

        doAnswer(invocation -> listeners.toArray(new WindowListener[0]))
            .when(window).getWindowListeners();

        return window;
    }
}
