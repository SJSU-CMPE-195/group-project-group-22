package edu.sjsu.spring2026.group32.pong.core;

import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent scoreboard state and rules for Pong matchups.
 */
public final class PongScoreboardService {
    /**
     * Accumulated points for a single head-to-head matchup.
     */
    public static final class MatchupRecord {
        private final PlayerVariant variantA;
        private final PlayerVariant variantB;
        private int pointsA;
        private int pointsB;

        public MatchupRecord(PlayerVariant variantA, PlayerVariant variantB, int pointsA, int pointsB) {
            this.variantA = variantA;
            this.variantB = variantB;
            this.pointsA = pointsA;
            this.pointsB = pointsB;
        }

        public PlayerVariant variantA() {
            return variantA;
        }

        public PlayerVariant variantB() {
            return variantB;
        }

        public int pointsA() {
            return pointsA;
        }

        public int pointsB() {
            return pointsB;
        }

        void incrementWinner(PlayerVariant winner, PlayerVariant loser) {
            if (winner.name().compareTo(loser.name()) <= 0) {
                pointsA++;
            } else {
                pointsB++;
            }
        }
    }

    private final Map<String, MatchupRecord> records = new LinkedHashMap<>();
    private final Path filePath;

    public PongScoreboardService(Path filePath) {
        this.filePath = filePath;
        load();
    }

    public void recordPoint(PlayerVariant winner, PlayerVariant loser) {
        if (winner == loser) {
            return;
        }

        String key = makeKey(winner, loser);
        records.computeIfAbsent(key, ignored -> {
            PlayerVariant[] sorted = sorted(winner, loser);
            return new MatchupRecord(sorted[0], sorted[1], 0, 0);
        });
        records.get(key).incrementWinner(winner, loser);
        save();
    }

    public void reset() {
        records.clear();
        save();
    }

    public Collection<MatchupRecord> getRecords() {
        return Collections.unmodifiableCollection(records.values());
    }

    private void save() {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(filePath))) {
            pw.println("# Pong Scoreboard");
            pw.println("# variantA,variantB,pointsA,pointsB");
            for (MatchupRecord rec : records.values()) {
                pw.printf("%s,%s,%d,%d%n",
                        rec.variantA().name(),
                        rec.variantB().name(),
                        rec.pointsA(),
                        rec.pointsB());
            }
        } catch (IOException e) {
            System.err.println("Scoreboard: could not save - " + e.getMessage());
        }
    }

    private void load() {
        if (!Files.exists(filePath)) {
            return;
        }

        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length != 4) {
                    continue;
                }
                try {
                    PlayerVariant a = PlayerVariant.valueOf(parts[0].trim());
                    PlayerVariant b = PlayerVariant.valueOf(parts[1].trim());
                    int pA = Integer.parseInt(parts[2].trim());
                    int pB = Integer.parseInt(parts[3].trim());
                    records.put(makeKey(a, b), new MatchupRecord(a, b, pA, pB));
                } catch (IllegalArgumentException ignored) {
                    // Unknown variant name or malformed int - skip line
                }
            }
        } catch (IOException e) {
            System.err.println("Scoreboard: could not load - " + e.getMessage());
        }
    }

    private static String makeKey(PlayerVariant a, PlayerVariant b) {
        PlayerVariant[] sorted = sorted(a, b);
        return sorted[0].name() + "," + sorted[1].name();
    }

    private static PlayerVariant[] sorted(PlayerVariant a, PlayerVariant b) {
        return a.name().compareTo(b.name()) <= 0
                ? new PlayerVariant[]{a, b}
                : new PlayerVariant[]{b, a};
    }
}
