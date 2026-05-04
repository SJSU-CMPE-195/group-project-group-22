package edu.sjsu.spring2026.group32.hitthezone.ai;

import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneAction;
import edu.sjsu.spring2026.group32.hitthezone.model.HitTheZoneState;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HitTheZoneSoftwareAI Suite")
class HitTheZoneSoftwareAITest {

    private static final HitTheZoneState IN_ZONE = new HitTheZoneState(true);
    private static final HitTheZoneState OUT_ZONE = new HitTheZoneState(false);

    // zero jitter, scores on the very first in-zone tick

    @Test
    @DisplayName("scoringDecisions: zero jitter scores on first in-zone tick")
    void scoringDecisions_coverPositiveAndNegativeCases_zeroJitter() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("Perfect", 0);

        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE), "zero jitter should score on the very first in-zone tick");

        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE), "each in-zone tick is treated as a new entry after score is consumed");
    }

    @Test
    @DisplayName("scoringDecisions: zero jitter scores repeatedly on each new zone entry")
    void scoringDecisions_coverPositiveAndNegativeCases_zeroJitter_multipleEntries() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("Perfect", 0);

        // first entry
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE), "first entry scores");
        ai.getNextMove(OUT_ZONE); // exit resets countdown

        // second entry
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE), "second entry scores");
        ai.getNextMove(OUT_ZONE);

        // third entry
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE), "third entry scores");
    }

    @Test
    @DisplayName("scoringDecisions: returns null when out of zone")
    void scoringDecisions_coverPositiveAndNegativeCases_outOfZone() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("Perfect", 0);

        assertNull(ai.getNextMove(OUT_ZONE), "out of zone before any entry → null");
        assertNull(ai.getNextMove(OUT_ZONE), "still out of zone → null");

        // score in zone then exit, out-of-zone ticks should all be null
        ai.getNextMove(IN_ZONE); // scores and resets
        assertNull(ai.getNextMove(OUT_ZONE), "out of zone after scoring → null");
        assertNull(ai.getNextMove(OUT_ZONE), "still out of zone → null");
    }

    // Jitter, countdown delays the score by 1...maxJitterTicks ticks

    @Test
    @DisplayName("scoringDecisions: jitter=1 scores on tick 1 or 2, never on tick 0")
    void scoringDecisions_coverPositiveAndNegativeCases_jitterCountdown() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("Jittery", 1);

        for (int entry = 0; entry < 20; entry++) {
            HitTheZoneAction first = ai.getNextMove(IN_ZONE);
            HitTheZoneAction second = ai.getNextMove(IN_ZONE);

            boolean scoredWithinTwoTicks = HitTheZoneAction.SCORE.equals(first) || HitTheZoneAction.SCORE.equals(second);

            assertEquals(true, scoredWithinTwoTicks, "jitter=1 should always score within 2 in-zone ticks (entry " + entry + ")");

            // exit to reset for next entry
            ai.getNextMove(OUT_ZONE);

        }
    }

    @Test
    @DisplayName("scoringDecisions: ball exits before countdown expires — opportunity missed")
    void scoringDecisions_coverPositiveAndNegativeCases_missedOpportunity() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("SlowReact", 10);

        int missCount = 0;
        for (int entry = 0; entry < 50; entry++) {
            // stay in zone for exactly 1 tick
            HitTheZoneAction result = ai.getNextMove(IN_ZONE);
            ai.getNextMove(OUT_ZONE); // exit immediately

            // out-of-zone tick after exit must always be null — no late scoring
            assertNull(ai.getNextMove(OUT_ZONE), "AI must not score out of zone after ball exits (entry " + entry + ")");

            if (result == null) missCount++;
        }

        assertEquals(true, missCount > 0, "with jitter=10, at least some single-tick zone visits should be missed");
    }

    @Test
    @DisplayName("scoringDecisions: negative maxJitterTicks is clamped to 0")
    void scoringDecisions_coverPositiveAndNegativeCases_negativeJitterClamped() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("Clamped", -5);

        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE), "negative jitter should be clamped to 0 and score immediately");
    }

    @Test
    @DisplayName("scoringDecisions: countdown resets cleanly on each new entry")
    void scoringDecisions_coverPositiveAndNegativeCases_countdownResetOnReentry() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("Reset", 0);

        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE));
        assertNull(ai.getNextMove(OUT_ZONE), "null after exit");

        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE), "fresh entry should score again — countdown reset on exit");
        assertNull(ai.getNextMove(OUT_ZONE), "null after second exit");

    }
}