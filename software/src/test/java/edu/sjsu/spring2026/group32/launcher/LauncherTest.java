package edu.sjsu.spring2026.group32.launcher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @DisplayName("SerialConnectionPanel is present and shared across games")
    void serialConnectionPanelsArePresent() {
        assertNotNull(launcher.htzPanel);
        assertNotNull(launcher.pongPanel);
        assertNotSame(launcher.htzPanel, launcher.pongPanel, "HTZ and Pong panels should be separate instances");

    }

    @Test
    @Disabled("Hardware connection simulation requires SerialConnectionPanel refactor - covered by manual testing")
    @DisplayName("Connecting hardware enables the HARDWARE player variant")
    void connectingHardwareEnablesHardwareVariant() {}

    @Test
    @DisplayName("Hardware managers are null before any connection")
    void hardwareManagersNullBeforeConnection() {
        // simulates initial state before a serial device is connected
        assertNull(launcher.htzManager, "HTZ manager should be null before connecting");
        assertNull(launcher.pongManager, "Pong manager should be null before connecting");

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
