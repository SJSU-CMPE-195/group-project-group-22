package edu.sjsu.spring2026.group32.launcher.core;

import edu.sjsu.spring2026.group32.testsupport.SerialTestRig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("LauncherHardwareMessages Suite")
class LauncherHardwareMessagesTest {

    // describeHitTheZoneLaunch

    @Test
    @DisplayName("HTZ: connected manager produces hardware-passed message")
    void describeLaunchMessages_coverPositiveAndNegativeStates_htzConnected() {
        SerialTestRig rig = SerialTestRig.createSingleChannelRig();
        rig.connectManagerToFakeDevice();
        awaitConnected(rig);

        assertEquals("  -> HTZ hardware passed to Hit The Zone.", LauncherHardwareMessages.describeHitTheZoneLaunch(rig.manager()), "connected manager should produce hardware-passed message");

        rig.manager().disconnect();
    }

    @Test
    @DisplayName("HTZ: disconnected manager produces not-connected message")
    void describeLaunchMessages_coverPositiveAndNegativeStates_htzDisconnected() {
        SerialTestRig rig = SerialTestRig.createSingleChannelRig();

        assertEquals("  -> HTZ device not connected. Launching without neural player.", LauncherHardwareMessages.describeHitTheZoneLaunch(rig.manager()), "disconnected manager should produce not-connected message");
    }

    @Test
    @DisplayName("HTZ: null manager produces not-connected message")
    void describeLaunchMessages_coverPositiveAndNegativeStates_htzNull() {
        assertEquals("  -> HTZ device not connected. Launching without neural player.", LauncherHardwareMessages.describeHitTheZoneLaunch(null), "null manager should produce not-connected message");
    }

    // describePongLaunch

    @Test
    @DisplayName("Pong: dual-channel connected manager produces hardware-passed message")
    void describeLaunchMessages_coverPositiveAndNegativeStates_pongDualChannel() {
        SerialTestRig rig = SerialTestRig.createDualChannelRig();
        rig.connectManagerToFakeDevice();
        awaitConnected(rig);

        assertEquals("  -> Pong hardware passed to Pong.", LauncherHardwareMessages.describePongLaunch(rig.manager()), "dual-channel connected manager should produce hardware-passed message");

        rig.manager().disconnect();
    }

    @Test
    @DisplayName("Pong: single-channel connected manager produces wrong-channel message")
    void describeLaunchMessages_coverPositiveAndNegativeStates_pongSingleChannel() {
        SerialTestRig rig = SerialTestRig.createSingleChannelRig();
        rig.connectManagerToFakeDevice();
        awaitConnected(rig);

        assertEquals("  -> Pong device has 1 ch (need 2). Launching without neural player.", LauncherHardwareMessages.describePongLaunch(rig.manager()), "single-channel connected manager should produce wrong-channel message");

        rig.manager().disconnect();
    }

    @Test
    @DisplayName("Pong: disconnected manager produces not-connected message")
    void describeLaunchMessages_coverPositiveAndNegativeStates_pongDisconnected() {
        SerialTestRig rig = SerialTestRig.createSingleChannelRig();

        assertEquals("  -> Pong device not connected. Launching without neural player.", LauncherHardwareMessages.describePongLaunch(rig.manager()), "disconnected manager should produce not-connected message");
    }

    @Test
    @DisplayName("Pong: null manager produces not-connected message")
    void describeLaunchMessages_coverPositiveAndNegativeStates_pongNull() {
        assertEquals( "  -> Pong device not connected. Launching without neural player.", LauncherHardwareMessages.describePongLaunch(null), "null manager should produce not-connected message");
    }

    // helper

    private static void awaitConnected(SerialTestRig rig) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (rig.manager().getDeviceChannelCount() == 0) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for manager to process boot sequence");
            }
            try { Thread.sleep(20); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for manager to connect");
            }
        }
    }
}