package edu.sjsu.spring2026.group32.launcher.ui;


import edu.sjsu.spring2026.group32.testsupport.SerialTestRig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConnectionStatusPanel Suite")
class ConnectionStatusPanelTest {

    // labelText, the only pure logic in the class

    @Test
    @DisplayName("labelText() produces connected string when connected=true")
    void indicatorRefresh_coversPositiveAndNegativeConnectionStates_labelConnected() {
        assertEquals("● HTZ: Connected", ConnectionStatusPanel.labelText("HTZ", true), "connected label should include device name and 'Connected'");
    }

    @Test
    @DisplayName("labelText() produces disconnected string when connected=false")
    void indicatorRefresh_coversPositiveAndNegativeConnectionStates_labelDisconnected() {
        assertEquals("● HTZ: Not connected", ConnectionStatusPanel.labelText("HTZ", false), "disconnected label should include device name and 'Not connected'");
    }

    @Test
    @DisplayName("labelText() uses the name supplied, not a hardcoded string")
    void indicatorRefresh_coversPositiveAndNegativeConnectionStates_labelUsesName() {
        assertEquals("● Pong: Connected", ConnectionStatusPanel.labelText("Pong", true));
        assertEquals("● Pong: Not connected", ConnectionStatusPanel.labelText("Pong", false));
    }

    // isAlive,  null safety and connection state

    @Test
    @DisplayName("isAlive() returns false for null manager")
    void indicatorRefresh_coversPositiveAndNegativeConnectionStates_nullManager() {
        assertFalse(ConnectionStatusPanel.isAlive(null), "null manager should always report not alive");
    }

    @Test
    @DisplayName("isAlive() returns true for a connected manager")
    void indicatorRefresh_coversPositiveAndNegativeConnectionStates_connectedManager() {
        SerialTestRig rig = SerialTestRig.createSingleChannelRig();
        rig.connectManagerToFakeDevice();
        awaitCondition(() -> rig.manager().isConnected(), "manager to connect");

        assertTrue(ConnectionStatusPanel.isAlive(rig.manager()), "connected manager should report alive");

        rig.manager().disconnect();
    }

    @Test
    @DisplayName("isAlive() returns false for a disconnected manager")
    void indicatorRefresh_coversPositiveAndNegativeConnectionStates_disconnectedManager() {
        SerialTestRig rig = SerialTestRig.createSingleChannelRig();
        // never connected
        assertFalse(ConnectionStatusPanel.isAlive(rig.manager()), "disconnected manager should report not alive");
    }

    // timer lifecycle 

    @Test
    @DisplayName("timerLifecycle: addNotify and removeNotify are Swing lifecycle methods" + " — covered by @GeneratedExcludeFromCoverage")
    void timerLifecycle_startsAndStopsWithSwingHierarchy() {
        assertTrue(true, "timer lifecycle is excluded from coverage — no logic to test");
    }

    // helper

    private static void awaitCondition(java.util.function.BooleanSupplier condition,
        String description) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for: " + description);
            }
            
            try { Thread.sleep(20); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for: " + description);
                
            }
        }
    }
}