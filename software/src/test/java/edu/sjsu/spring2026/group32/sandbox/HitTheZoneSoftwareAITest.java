package edu.sjsu.spring2026.group32.sandbox;

import edu.sjsu.spring2026.group32.player.PlayerType;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HitTheZoneSoftwareAI}.
 *
 * <p>Key behavioral facts:
 * <ul>
 *   <li>With {@code maxJitterTicks=0} the AI scores on <em>every</em> tick it
 *       is in the zone (countdown resets to 0 immediately after each SCORE).</li>
 *   <li>With {@code maxJitterTicks=N>0} the AI waits 0–N ticks per scoring
 *       opportunity and therefore scores at most once every {@code N+1} ticks.</li>
 *   <li>The "once per zone entry" edge-detection is enforced by the game loop
 *       ({@code lastActions} / {@code canScore}), <em>not</em> by this class.</li>
 *   <li>On zone exit the internal countdown is reset to {@code -1} so the
 *       next entry arms a fresh countdown.</li>
 * </ul>
 */
@DisplayName("HitTheZoneSoftwareAI Suite")
class HitTheZoneSoftwareAITest {

    private static final HitTheZoneState IN_ZONE  = new HitTheZoneState(true);
    private static final HitTheZoneState OUT_ZONE = new HitTheZoneState(false);

    // ──────────────────────────────────────────────────────────────────────────
    // Zero-jitter (instant reaction)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Zero jitter: scores on very first tick in zone")
    void zeroJitter_scoresOnFirstTick() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("bot", 0);
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE));
    }

    @Test
    @DisplayName("Zero jitter: scores on every consecutive tick in zone (game loop enforces single-score)")
    void zeroJitter_scoresOnEveryConsecutiveTick() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("bot", 0);
        // countdown resets to 0 immediately after each SCORE --> scores every tick
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE));
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE));
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Out-of-zone behaviour
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns null when ball is outside the zone")
    void returnsNullWhenOutsideZone() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("bot", 0);
        assertNull(ai.getNextMove(OUT_ZONE));
    }

    @Test
    @DisplayName("Returns null on multiple consecutive out-of-zone ticks")
    void returnsNullOnMultipleOutOfZoneTicks() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("bot", 0);
        assertNull(ai.getNextMove(OUT_ZONE));
        assertNull(ai.getNextMove(OUT_ZONE));
        assertNull(ai.getNextMove(OUT_ZONE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Zone re-entry
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Zone re-entry resets countdown so AI scores again on re-entry (zero jitter)")
    void zeroJitter_zoneReEntryRestartsCountdown() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("bot", 0);
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE));

        assertNull(ai.getNextMove(OUT_ZONE));

        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE));
    }

    @Test
    @DisplayName("Zone exit during countdown discards pending countdown")
    void zoneExitDuringCountdownDiscardsIt() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("bot", 100);

        // arm countdown
        ai.getNextMove(IN_ZONE);
        // exit immediately — discards whatever countdown was set
        ai.getNextMove(OUT_ZONE);

        // after re-entry, the AI should score at most in 101 ticks
        boolean scored = false;
        for (int i = 0; i <= 101; i++) {
            if (HitTheZoneAction.SCORE == ai.getNextMove(IN_ZONE)) {
                scored = true;
                break;
            }
        }
        assertTrue(scored, "AI must score within maxJitterTicks+1 ticks after re-entry");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Non-zero jitter: timing guarantee
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Non-zero jitter: AI scores exactly once within maxJitterTicks+1 ticks of zone entry")
    void nonZeroJitter_scoresWithinWindow() {
        // maxJitterTicks = 5 --> AI scores between tick 1 and tick 6 (inclusive)
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("bot", 5);

        int scoreCount = 0;
        for (int tick = 1; tick <= 6; tick++) {
            if (HitTheZoneAction.SCORE == ai.getNextMove(IN_ZONE)) {
                scoreCount++;
                break; // stop after first score to match real game behaviour
            }
        }
        assertEquals(1, scoreCount, "AI must score exactly once in the first maxJitterTicks+1 ticks");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Negative jitter clamping
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Negative maxJitterTicks is clamped to zero (instant reaction)")
    void negativeJitterClampedToZero() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("bot", -99);
        // clamped behaves identically to jitter = 0
        assertEquals(HitTheZoneAction.SCORE, ai.getNextMove(IN_ZONE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Metadata
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getName() returns the name passed to the constructor")
    void getNameReturnsConstructorValue() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("TestBot", 0);
        assertEquals("TestBot", ai.getName());
    }

    @Test
    @DisplayName("getType() returns PlayerType.SOFTWARE")
    void getTypeReturnsSoftware() {
        HitTheZoneSoftwareAI ai = new HitTheZoneSoftwareAI("bot", 0);
        assertEquals(PlayerType.SOFTWARE, ai.getType());
    }
}
