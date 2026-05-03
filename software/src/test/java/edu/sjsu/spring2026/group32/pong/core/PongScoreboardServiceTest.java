package edu.sjsu.spring2026.group32.pong.core;

import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;
import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


@DisplayName("PongScoreboardService Suite")
class PongScoreboardServiceTest {

    @TempDir
    Path tempDir;

    private Path scoreFile;
    private PongScoreboardService service;

    @BeforeEach
    void setUp() {
        scoreFile = tempDir.resolve("test_scores.csv");
        service = new PongScoreboardService(scoreFile);
    }

    @Test
    @DisplayName("recordPoint: creates canonical records and ignores self-matchups")
    void recordPoint_createsCanonicalRecords() {
        service.recordPoint(PlayerVariant.HUMAN, PlayerVariant.HUMAN);
        assertTrue(service.getRecords().isEmpty(), "Self-matchups should be ignored");

        service.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_HARD); // Human wins
        service.recordPoint(PlayerVariant.AI_HARD, PlayerVariant.HUMAN); // AI wins

        Collection<PongScoreboardService.MatchupRecord> records = service.getRecords();
        assertEquals(1, records.size(), "A vs B and B vs A should use the same canonical record");

        PongScoreboardService.MatchupRecord record = records.iterator().next();
        
        assertEquals(PlayerVariant.AI_HARD, record.variantA());
        assertEquals(PlayerVariant.HUMAN, record.variantB());
        
        assertEquals(1, record.pointsA()); // AI_HARD win
        assertEquals(1, record.pointsB()); // HUMAN win
    }

    @Test
    @DisplayName("persistenceAndReset: covers save/load and malformed data paths")
    void persistenceAndReset_coverPositiveAndNegativePaths() throws IOException {

        service.recordPoint(PlayerVariant.HUMAN, PlayerVariant.AI_EASY);
        assertTrue(Files.exists(scoreFile), "File should be created on save");

        PongScoreboardService newService = new PongScoreboardService(scoreFile);
        assertEquals(1, newService.getRecords().size(), "New service should load existing data");

        Files.writeString(scoreFile, "\nGARBAGE,DATA,NOT,NUMBERS\nCORRECT,FORMAT,BUT,WRONG_ENUM", java.nio.file.StandardOpenOption.APPEND);
        
        PongScoreboardService resilientService = new PongScoreboardService(scoreFile);
        assertEquals(1, resilientService.getRecords().size(), "Service should skip malformed lines");

        resilientService.reset();
        assertEquals(0, resilientService.getRecords().size());
        
        String content = Files.readString(scoreFile);
        assertFalse(content.contains("HUMAN"), "File should not contain records after reset");
    }

    @Test
    @DisplayName("getRecords: returns immutable view")
    void getRecords_returnsImmutableView() {
        Collection<PongScoreboardService.MatchupRecord> records = service.getRecords();
        
        assertThrows(UnsupportedOperationException.class, () -> { records.clear();}, "The collection returned should be unmodifiable");
        
    }
}