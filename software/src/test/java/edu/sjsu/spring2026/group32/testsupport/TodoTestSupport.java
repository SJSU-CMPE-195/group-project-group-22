package edu.sjsu.spring2026.group32.testsupport;

import org.junit.jupiter.api.Assertions;

/**
 * Shared helper for boilerplate placeholder tests.
 */
public final class TodoTestSupport {
    private TodoTestSupport() {
    }

    public static void todo(String message) {
        Assertions.fail("TODO: " + message);
    }
}
