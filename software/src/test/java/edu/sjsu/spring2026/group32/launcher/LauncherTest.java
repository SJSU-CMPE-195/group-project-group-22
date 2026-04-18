package edu.sjsu.spring2026.group32.launcher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.*;

/**
 * Integration tests for {@link Launcher}.
 *
 * <p><b>NOTE:</b> {@code Launcher} creates a full Swing {@code JFrame} and
 * multiple game panels, requiring a real or virtual (xvfb) display.
 * All methods are boilerplate stubs marked with TODO.
 */
@DisplayName("Launcher Suite")
class LauncherTest {
    private Launcher launcher;

    // ──────────────────────────────────────────────────────────────────────────
    // Startup
    // ──────────────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> launcher = new Launcher()); 

    }

    @AfterEach
    void tearDown() throws Exception {
        if(launcher != null) {
            SwingUtilities.invokeAndWait(() -> launcher.dispose());
        }
    }

    @Test
    @DisplayName("Launcher JFrame is visible after construction") // checking if launcher actually makes window visible
    void launcherFrameIsVisible() throws Exception {
        SwingUtilities.invokeAndWait(() -> launcher.setVisible(true)); 
        assertTrue(launcher.isVisible(), "Launcher JFrame should be visible after setVisible(true)");

    }

    @Test
    @DisplayName("Launcher contains the Pong game panel")
    void launcherContainsPongPanel() {
        assertNotNull(launcher.pongPanel, "Pong SerialConnectionPanel should be initialized");
    }

    @Test
    @DisplayName("Launcher contains the HitTheZone panel")
    void launcherContainsHitTheZonePanel() {
        assertNotNull(launcher.htzPanel, "HTZ SerialConnectionPanel should be initialized");

    }

    // ──────────────────────────────────────────────────────────────────────────
    // Serial connection panel integration
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TODO: SerialConnectionPanel is present and shared across games")
    void serialConnectionPanelIsPresent() {
        // TODO: locate SerialConnectionPanel in the component hierarchy
    }

    @Test
    @DisplayName("TODO: Connecting hardware enables the HARDWARE player variant")
    void connectingHardwareEnablesHardwareVariant() {
        // TODO: simulate a successful serial connection, assert HARDWARE is enabled in toolbar
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Shutdown
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TODO: Closing the JFrame triggers port disconnection")
    void closingFrameDisconnectsPort() {
        // TODO: dispatch WINDOW_CLOSING event, verify disconnect() was called on manager
    }
}
