package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;

@DisplayName("PongToolbar Suite")
class PongToolbarTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static final PlayerVariant[] ALL_VARIANTS = PlayerVariant.values();

    private PongToolbar toolbar;
    private List<PlayerVariant>  variantChanges;
    private Scoreboard scoreboard;

    @BeforeEach
    void setUp() {
        variantChanges = new ArrayList<>();
        scoreboard = new Scoreboard();

        toolbar = new PongToolbar(PongToolbar.Side.TOP, ALL_VARIANTS, PlayerVariant.AI_EASY, true, scoreboard);

        toolbar.setOnVariantChanged(variantChanges::add);
        
    }

    // selection — valid variants
    
    @Test
    @DisplayName("selecting a valid variant fires the callback and updates lastValidSelection")
    void selectionRules_validSelection() {
        toolbar.dropdown.setSelectedItem(PlayerVariant.AI_HARD);

        assertEquals(PlayerVariant.AI_HARD, toolbar.getSelectedVariant(), "getSelectedVariant() should reflect the new selection");
        assertEquals(1, variantChanges.size(), "callback should fire once");

        assertEquals(PlayerVariant.AI_HARD, variantChanges.get(0), "callback should receive the new variant");

    }

    @Test
    @DisplayName("selecting the same variant again does not fire the callback")
    void selectionRules_sameVariantNoCallback() {
        toolbar.dropdown.setSelectedItem(PlayerVariant.AI_EASY);

        assertTrue(variantChanges.isEmpty(), "callback should not fire when selection does not change");

    }

    @Test
    @DisplayName("selecting HARDWARE when unavailable reverts to previous selection silently")
    void selectionRules_hardwareUnavailableReverts() {
        toolbar.setHardwareAvailable(false);
        variantChanges.clear();

        toolbar.dropdown.setSelectedItem(PlayerVariant.HARDWARE);

        assertEquals(PlayerVariant.AI_EASY, toolbar.getSelectedVariant(), "selection should revert to last valid when HARDWARE is unavailable");
        assertTrue(variantChanges.isEmpty(), "callback should not fire when selection is reverted");

        assertEquals(PlayerVariant.AI_EASY, toolbar.dropdown.getSelectedItem(), "dropdown should show the reverted value");

    }

    @Test
    @DisplayName("selecting HARDWARE when available is accepted and fires callback")
    void selectionRules_hardwareAvailableAccepted() {
        toolbar.setHardwareAvailable(true);
        toolbar.dropdown.setSelectedItem(PlayerVariant.HARDWARE);

        assertEquals(PlayerVariant.HARDWARE, toolbar.getSelectedVariant(), "HARDWARE should be accepted when available");

        assertEquals(1, variantChanges.size(), "callback should fire");
        assertEquals(PlayerVariant.HARDWARE, variantChanges.get(0));

    }

    @Test
    @DisplayName("setOnVariantChanged(null) — selection changes without NPE")
    void selectionRules_nullCallbackSafe() {
        toolbar.setOnVariantChanged(null);

        toolbar.dropdown.setSelectedItem(PlayerVariant.AI_HARD);
        assertEquals(PlayerVariant.AI_HARD, toolbar.getSelectedVariant());

    }

    // lock state
    
    @Test
    @DisplayName("setSelectionLocked(true) disables the dropdown")
    void selectionRules_lockedDisablesDropdown() {
        toolbar.setSelectionLocked(true);

        assertFalse(toolbar.dropdown.isEnabled(), "dropdown should be disabled when locked");

    }

    @Test
    @DisplayName("setSelectionLocked(false) re-enables the dropdown")
    void selectionRules_unlockedEnablesDropdown() {
        toolbar.setSelectionLocked(true);
        toolbar.setSelectionLocked(false);

        assertTrue(toolbar.dropdown.isEnabled(), "dropdown should be enabled when unlocked");

    }

    @Test
    @DisplayName("locked toolbar still holds the last valid selection")
    void selectionRules_lockedPreservesSelection() {
        toolbar.dropdown.setSelectedItem(PlayerVariant.AI_HARD);
        toolbar.setSelectionLocked(true);

        assertEquals(PlayerVariant.AI_HARD, toolbar.getSelectedVariant(), "selection should be preserved when locked");

    }

    // hardware controls
    
    @Test
    @DisplayName("hardware settings button is visible only when HARDWARE is selected and available")
    void hardwareControls_updateFromToolbarState_settingsButtonVisibility() {
        toolbar.setHardwareAvailable(true);
        toolbar.setSelectedVariant(PlayerVariant.HARDWARE);

        assertTrue(toolbar.hardwareSettingsButton.isVisible(), "hardware settings button should be visible when HARDWARE is selected and available");

        toolbar.setSelectedVariant(PlayerVariant.AI_EASY);
        assertFalse(toolbar.hardwareSettingsButton.isVisible(), "hardware settings button should hide when non-HARDWARE variant is selected");

    }

    @Test
    @DisplayName("hardware settings button is hidden when hardware is unavailable even if HARDWARE is selected")
    void hardwareControls_updateFromToolbarState_settingsButtonHiddenWhenUnavailable() {
        toolbar.setHardwareAvailable(false);

        toolbar.setSelectedVariant(PlayerVariant.AI_HARD); // can't select HARDWARE when unavailable
        assertFalse(toolbar.hardwareSettingsButton.isVisible(), "hardware settings button should be hidden when hardware is unavailable");

    }

    @Test
    @DisplayName("setEventCounts() updates the channel spike label text")
    void hardwareControls_updateFromToolbarState_eventCountLabel() {
        toolbar.setEventCounts(3, 7);
        assertEquals("Ch1 Spikes: 3  Ch2 Spikes: 7", toolbar.channelSpikeLabel.getText(), "spike label should reflect updated channel counts");

    }

    @Test
    @DisplayName("setEventCounts() with zero values shows zero counts")
    void hardwareControls_updateFromToolbarState_eventCountLabelZero() {
        toolbar.setEventCounts(0, 0);
        assertEquals("Ch1 Spikes: 0  Ch2 Spikes: 0", toolbar.channelSpikeLabel.getText());

    }

    @Test
    @DisplayName("setHardwareAvailable(false) hides hardware settings button")
    void hardwareControls_updateFromToolbarState_hardwareUnavailableHidesSettings() {
        toolbar.setHardwareAvailable(true);
        toolbar.setSelectedVariant(PlayerVariant.HARDWARE);

        assertTrue(toolbar.hardwareSettingsButton.isVisible());

        toolbar.setHardwareAvailable(false);
        assertFalse(toolbar.hardwareSettingsButton.isVisible(), "settings button should hide when hardware becomes unavailable");

    }

    @Test
    @DisplayName("setSelectionLocked(true) disables hardware settings button when visible")
    void hardwareControls_updateFromToolbarState_lockDisablesSettingsButton() {
        toolbar.setHardwareAvailable(true);
        toolbar.setSelectedVariant(PlayerVariant.HARDWARE);

        assertTrue(toolbar.hardwareSettingsButton.isEnabled());

        toolbar.setSelectionLocked(true);
        assertFalse(toolbar.hardwareSettingsButton.isEnabled(), "hardware settings button should be disabled when toolbar is locked");

    }
}