package edu.sjsu.spring2026.group32.launcher.core;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;

/**
 * User-facing launcher log messages related to connected hardware.
 */
public final class LauncherHardwareMessages {
    private LauncherHardwareMessages() {
    }

    public static String describeHitTheZoneLaunch(SerialConnectionManager manager) {
        if (manager != null && manager.isConnected()) {
            return "  -> HTZ hardware passed to Hit The Zone.";
        }
        return "  -> HTZ device not connected. Launching without neural player.";
    }

    public static String describePongLaunch(SerialConnectionManager manager) {
        if (manager != null && manager.isConnected() && manager.getDeviceChannelCount() >= 2) {
            return "  -> Pong hardware passed to Pong.";
        }
        if (manager != null && manager.isConnected()) {
            return "  -> Pong device has " + manager.getDeviceChannelCount()
                    + " ch (need 2). Launching without neural player.";
        }
        return "  -> Pong device not connected. Launching without neural player.";
    }
}
