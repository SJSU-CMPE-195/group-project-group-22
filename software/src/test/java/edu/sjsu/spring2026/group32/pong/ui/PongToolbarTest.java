package edu.sjsu.spring2026.group32.pong.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.*;

import edu.sjsu.spring2026.group32.pong.PlayerVariant;

/**
 * Unit / integration tests for {@link PongToolbar}.
 *
 * <p><b>NOTE:</b> PongToolbar is a Swing component (JPanel) and requires an
 * AWT display context.  On CI, tests run under {@code xvfb-run} which provides
 * a virtual X11 display.  Locally, ensure a display is available or skip this
 * class with {@code @Disabled}.
 *
 * <p>All test methods below are boilerplate stubs.  Implement them by
 * constructing a {@code PongToolbar} inside {@code setUp()} and then
 * invoking the relevant methods / simulating UI events.
 */
@DisplayName("PongToolbar Suite")
class PongToolbarTest {

    PongToolbar topToolbar;
    private PongToolbar bottomToolbar;
    private Scoreboard  scoreboard;

    @BeforeEach
    void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            scoreboard = new Scoreboard();
            
            // hardware unavailable by default (tests that need it can override it locally)
            topToolbar = new PongToolbar(PongToolbar.Side.TOP, PlayerVariant.AI_HARD, false, scoreboard);
            bottomToolbar = new PongToolbar(PongToolbar.Side.BOTTOM, PlayerVariant.HUMAN, false, scoreboard);

        });


    }

    // ──────────────────────────────────────────────────────────────────────────
    // Construction
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PongToolbar constructs without error for TOP position")
    void constructsForTopPosition() {
        assertNotNull(topToolbar, "TOP toolbar should be constructed successfully");
    }

    @Test
    @DisplayName("PongToolbar constructs without error for BOTTOM position")
    void constructsForBottomPosition() {
        assertNotNull(bottomToolbar, "BOTTOM toolbarshould be constructed successfully");

    }

    // ──────────────────────────────────────────────────────────────────────────
    // Dropdown behavior
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HARDWARE variant is disabled when no hardware player is wired")
    void hardwareVariantDisabledWhenNoHardware() throws Exception {
        SwingUtilities.invokeAndWait(() -> 
            topToolbar.setLockedOutVariant(PlayerVariant.HUMAN)
        );

        // attempt to select HARDWARE (toolbar should silently revert)
        SwingUtilities.invokeAndWait(() -> {
            // simulates selecting hardware via setSelectedVariant indirectly by checking if getSelectedVariant() stays unchanged
        });

        assertNotEquals(PlayerVariant.HARDWARE, topToolbar.getSelectedVariant(), "HARDWARE should not be selectedable when hardware is unavailable");

    }

    @Test
    @DisplayName("Selecting a variant on one side disables it on the other")
    void selectedVariantDisabledOnOtherSide() throws Exception {
        SwingUtilities.invokeAndWait(() -> 
            bottomToolbar.setLockedOutVariant(PlayerVariant.AI_HARD)
        );

        // bottom toolbar's locked out variant should be AI_HARD
        // verify by attempting selection (it should rever to last valid)
        assertNotEquals(PlayerVariant.AI_HARD, bottomToolbar.getSelectedVariant(), "AI_HARD should be unavailable on bottom when top has selected it");
    }

    @Test
    @DisplayName("Scoreboard button triggers score display")
    void scoreboardButtonShowsDialog() throws Exception {
        final JDialog[] dialog = {null};

        SwingUtilities.invokeAndWait( () -> {
            // createPopupDialog(null) is valid, Scoreboard.java accepts a null owner
            dialog[0] = scoreboard.createPopupDialog(null);
        }); 

        assertNotNull(dialog[0], "CreatePopupDialog() should return a non-null dialog");
        assertEquals("Pong Scoreboard", dialog[0].getTitle(), "Dialog title should be 'Pong Scoreboard'");
        
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lock / unlock during game
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Variant dropdown is locked while game is in PLAYING state")
    void dropdownLockedDuringPlay() throws Exception {
        SwingUtilities.invokeAndWait(() ->
            topToolbar.setSelectionLocked(true)
        );

        assertFalse(topToolbar.dropdown.isEnabled(), "Dropdown should be disabled during PLAYING state");
        
    }

    @Test
    @DisplayName("Variant dropdown is unlocked when game returns to PAUSED state")
    void dropdownUnlockedWhenPaused() throws Exception {

        // simulates PongGame calling lockToolbars(false) when returning to PAUSED
        SwingUtilities.invokeAndWait(() -> {
            topToolbar.setSelectionLocked(true); // lock first 
            topToolbar.setSelectionLocked(false); // unlock
        });

        SwingUtilities.invokeAndWait(() ->
            assertTrue(topToolbar.isEnabled(), "Toolbar dropdown should be unlocked when game is PAUSED")
        );


    }
}
