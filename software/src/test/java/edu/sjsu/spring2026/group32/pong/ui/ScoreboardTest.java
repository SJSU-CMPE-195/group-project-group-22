package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.pong.PlayerVariant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Scoreboard}.
 *
 * <p>Uses JUnit 5's {@code @TempDir} to provide an isolated temporary
 * directory so tests never touch the real {@code pong_scoreboard.txt} file.
 */
@DisplayName("Scoreboard Suite")
class ScoreboardTest {

    @TempDir
    Path tempDir;

    private Scoreboard scoreboard;

    @BeforeEach
    void setUp() {
        scoreboard = new Scoreboard(tempDir.resolve("test_scoreboard.txt"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // recordPoint() — basic scoring
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordPoint() adds one point to the winner's tally")
    void recordPointIncrementsWinner() {
        scoreboard.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_EASY);

        Scoreboard.MatchupRecord rec = getRecord(PlayerVariant.HUMAN, PlayerVariant.AI_EASY);
        assertNotNull(rec, "Record should be created after first point");

        // HUMAN sorts before AI_EASY alphabetically? H < A? No: H > A, so AI_EASY is variantA
        // alphanumeric sort based on AI_EASY < HUMAN variantA = AI_EASY, variantB = HUMAN
        // if HUMAN wins then pointsB++
        assertEquals(1, rec.pointsB, "HUMAN (variantB) should have 1 point");
        assertEquals(0, rec.pointsA, "AI_EASY (variantA) should have 0 points");
    }

    @Test
    @DisplayName("recordPoint() for each variant accumulates correctly")
    void recordPointAccumulatesCorrectly() {
        scoreboard.recordPoint(PlayerVariant.AI_EASY, PlayerVariant.AI_HARD);
        scoreboard.recordPoint(PlayerVariant.AI_EASY, PlayerVariant.AI_HARD);
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.AI_EASY);

        // AI_EASY sorts before AI_HARD → variantA=AI_EASY
        Scoreboard.MatchupRecord rec = getRecord(PlayerVariant.AI_EASY, PlayerVariant.AI_HARD);
        assertNotNull(rec);
        assertEquals(2, rec.pointsA, "AI_EASY (variantA) scored twice");
        assertEquals(1, rec.pointsB, "AI_HARD (variantB) scored once");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Self-matchup guard
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordPoint() silently ignores self-matchups")
    void recordPointIgnoresSelfMatchup() {
        scoreboard.recordPoint(PlayerVariant.HUMAN, PlayerVariant.HUMAN);
        assertTrue(scoreboard.getRecords().isEmpty(), "No record should be created for a self-matchup");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Alphabetical key symmetry
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-->B and B-->A resolve to the same matchup record (alphabetical key)")
    void symmetricKeyResolution() {
        scoreboard.recordPoint(PlayerVariant.HUMAN,   PlayerVariant.AI_HARD);
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.HUMAN);

        // should be a single record with 1 point per side
        assertEquals(1, scoreboard.getRecords().size(), "A→B and B→A should map to the same record");

        Scoreboard.MatchupRecord rec = scoreboard.getRecords().iterator().next();
        int totalPoints = rec.pointsA + rec.pointsB;

        assertEquals(2, totalPoints, "Total should be 2 (one per side)");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Persistence: save & reload
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Points are persisted to file and loaded by a new Scoreboard instance")
    void persistenceRoundTrip() {
        Path file = tempDir.resolve("persist_test.txt");
        Scoreboard sb1 = new Scoreboard(file);

        sb1.recordPoint(PlayerVariant.AI_EASY, PlayerVariant.AI_HARD);
        sb1.recordPoint(PlayerVariant.AI_EASY, PlayerVariant.AI_HARD);

        // loads a fresh instance from the same file
        Scoreboard sb2 = new Scoreboard(file);
        Scoreboard.MatchupRecord rec = findRecord(sb2.getRecords(), PlayerVariant.AI_EASY, PlayerVariant.AI_HARD);

        assertNotNull(rec, "Record should survive save/load");
        assertEquals(2, rec.pointsA + rec.pointsB, "Total of 2 points should be loaded");
    }

    @Test
    @DisplayName("Loading from a non-existent file starts with empty records")
    void loadNonExistentFileStartsEmpty() {
        Path missing = tempDir.resolve("does_not_exist.txt");
        Scoreboard sb = new Scoreboard(missing);

        assertTrue(sb.getRecords().isEmpty());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Malformed file data
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Malformed lines in file are silently skipped")
    void malformedLinesAreSkipped() throws Exception {
        Path file = tempDir.resolve("malformed.txt");
        Files.writeString(file,
            "# Pong Scoreboard\n" +
            "NOTAVARIANT,AI_EASY,1,2\n" +    // unknown variant name
            "AI_EASY,AI_HARD,abc,2\n" +      // non-numeric score
            "HUMAN,AI_EASY\n" +              // too few fields
            "AI_EASY,AI_HARD,3,1\n");        // valid line

        Scoreboard sb = new Scoreboard(file);
        assertEquals(1, sb.getRecords().size(), "Only the valid line should be loaded");
    }

    @Test
    @DisplayName("Comment lines and blank lines in file are ignored")
    void commentsAndBlankLinesIgnored() throws Exception {
        Path file = tempDir.resolve("comments.txt");
        Files.writeString(file,
            "# Pong Scoreboard\n" +
            "# variantA,variantB,pointsA,pointsB\n" +
            "\n" +
            "AI_EASY,AI_HARD,5,3\n");

        Scoreboard sb = new Scoreboard(file);
        assertEquals(1, sb.getRecords().size());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // reset()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reset() clears all in-memory records")
    void resetClearsRecords() {
        scoreboard.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_EASY);
        scoreboard.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.AI_EASY);
        assertFalse(scoreboard.getRecords().isEmpty());

        scoreboard.reset();
        assertTrue(scoreboard.getRecords().isEmpty());
    }

    @Test
    @DisplayName("reset() clears persisted data (reload returns empty)")
    void resetClearsPersistence() {
        Path file = tempDir.resolve("reset_test.txt");
        Scoreboard sb = new Scoreboard(file);
        sb.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_EASY);
        sb.reset();

        Scoreboard sb2 = new Scoreboard(file);
        assertTrue(sb2.getRecords().isEmpty(), "File should be empty after reset");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getRecords() immutability
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getRecords() returns an unmodifiable collection")
    void getRecordsIsUnmodifiable() {
        scoreboard.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_EASY);
        Collection<Scoreboard.MatchupRecord> records = scoreboard.getRecords();
        assertThrows(UnsupportedOperationException.class, () -> records.add(null), "Returned collection should be unmodifiable");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Multiple matchups coexist
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Multiple distinct matchups are tracked independently")
    void multipleMatchupsAreIndependent() {
        scoreboard.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_EASY);
        scoreboard.recordPoint(PlayerVariant.AI_EASY, PlayerVariant.AI_HARD);
        scoreboard.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_HARD);

        assertEquals(3, scoreboard.getRecords().size(), "Three distinct matchups should each have their own record");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Finds the record for the given pair (order-independent). */
    private Scoreboard.MatchupRecord getRecord(PlayerVariant a, PlayerVariant b) {
        return findRecord(scoreboard.getRecords(), a, b);
    }

    private static Scoreboard.MatchupRecord findRecord(
            Collection<Scoreboard.MatchupRecord> records,
            PlayerVariant a, PlayerVariant b) {
        for (Scoreboard.MatchupRecord r : records) {
            if ((r.variantA == a && r.variantB == b) ||
                (r.variantA == b && r.variantB == a)) {
                return r;
            }
        }
        return null;
    }
}
