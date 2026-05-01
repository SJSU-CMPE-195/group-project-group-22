package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Persistent scoreboard for Pong.
 *
 * <p>Tracks cumulative points scored per matchup between <em>different</em>
 * player variants.  Self-matchups are excluded by the game rules and will
 * never be recorded here.
 *
 * <h3>Storage ({@value #FILE_NAME} in the working directory)</h3>
 * <pre>
 * # Pong Scoreboard
 * # variantA,variantB,pointsA,pointsB
 * HUMAN,AI_EASY,3,5
 * AI_EASY,AI_HARD,10,4
 * </pre>
 *
 * <p>The key is always alphabetically sorted so {@code A→B} and {@code B→A}
 * resolve to the same record regardless of which side scored first.
 */
public class Scoreboard {

    static final String FILE_NAME = "pong_scoreboard.txt";

    // ─── Inner model ──────────────────────────────────────────────────────────

    /** Accumulated points for a single head-to-head matchup. */
    public static class MatchupRecord {
        public final PlayerVariant variantA;   // alphabetically first
        public final PlayerVariant variantB;
        public int pointsA;
        public int pointsB;

        MatchupRecord(PlayerVariant a, PlayerVariant b, int pA, int pB) {
            this.variantA = a;
            this.variantB = b;
            this.pointsA  = pA;
            this.pointsB  = pB;
        }
    }

    // ─── State ───────────────────────────────────────────────────────────────

    /** Key = {@code "VARIANT_A,VARIANT_B"} (always alphabetically sorted). */
    private final Map<String, MatchupRecord> records = new LinkedHashMap<>();
    private final Path filePath;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /** Default constructor: saves to {@value #FILE_NAME} in the working directory. */
    public Scoreboard() {
        this(Paths.get(FILE_NAME));
    }

    /** Package-private constructor for testing with a custom path. */
    Scoreboard(Path filePath) {
        this.filePath = filePath;
        load();
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Records one point scored by {@code winner} against {@code loser}.
     * Silently ignores self-matchups ({@code winner == loser}).
     */
    public void recordPoint(PlayerVariant winner, PlayerVariant loser) {
        if (winner == loser) return;

        String key = makeKey(winner, loser);
        records.computeIfAbsent(key, k -> {
            PlayerVariant[] s = sorted(winner, loser);
            return new MatchupRecord(s[0], s[1], 0, 0);
        });

        MatchupRecord rec = records.get(key);
        // winner is variantA when its name comes first alphabetically
        if (winner.name().compareTo(loser.name()) <= 0) rec.pointsA++;
        else                                             rec.pointsB++;

        save();
    }

    /** Clears all records from memory and disk. */
    public void reset() {
        records.clear();
        save();
    }

    /** Returns an unmodifiable view of all matchup records. */
    public Collection<MatchupRecord> getRecords() {
        return Collections.unmodifiableCollection(records.values());
    }

    /**
     * Creates and returns a modal popup dialog showing all matchup records.
     *
     * @param owner parent frame for centering; may be {@code null}
     */
    public JDialog createPopupDialog(JFrame owner) {
        JDialog dialog = new JDialog(owner, "Pong Scoreboard", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // ── Table ────────────────────────────────────────────────────────────
        String[] cols = {"Player A", "Player B", "A Points", "B Points"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (MatchupRecord rec : records.values()) {
            model.addRow(new Object[]{
                    rec.variantA.getDisplayName(),
                    rec.variantB.getDisplayName(),
                    rec.pointsA,
                    rec.pointsB
            });
        }

        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.setFillsViewportHeight(true);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(420, 200));

        // ── Reset button ─────────────────────────────────────────────────────
        JButton resetBtn = new JButton("Reset All Scores");
        resetBtn.setForeground(new Color(180, 20, 20));
        resetBtn.setFocusPainted(false);
        resetBtn.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(dialog,
                    "Clear all scores? This cannot be undone.",
                    "Confirm Reset", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                reset();
                while (model.getRowCount() > 0) model.removeRow(0);
            }
        });

        // ── Close button ─────────────────────────────────────────────────────
        JButton closeBtn = new JButton("Close");
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> dialog.dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        btnRow.add(resetBtn);
        btnRow.add(closeBtn);

        // ── Root ─────────────────────────────────────────────────────────────
        JLabel heading = new JLabel(
                "Head-to-head points per matchup  (grouped by variant pair)");
        heading.setFont(new Font("SansSerif", Font.PLAIN, 12));
        heading.setForeground(Color.DARK_GRAY);

        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(10, 10, 6, 10));
        root.add(heading, BorderLayout.NORTH);
        root.add(scroll,  BorderLayout.CENTER);
        root.add(btnRow,  BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        return dialog;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private void save() {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(filePath))) {
            pw.println("# Pong Scoreboard");
            pw.println("# variantA,variantB,pointsA,pointsB");
            for (MatchupRecord rec : records.values()) {
                pw.printf("%s,%s,%d,%d%n",
                        rec.variantA.name(), rec.variantB.name(),
                        rec.pointsA, rec.pointsB);
            }
        } catch (IOException e) {
            System.err.println("Scoreboard: could not save — " + e.getMessage());
        }
    }

    private void load() {
        if (!Files.exists(filePath)) return;
        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts.length != 4) continue;
                try {
                    PlayerVariant a  = PlayerVariant.valueOf(parts[0].trim());
                    PlayerVariant b  = PlayerVariant.valueOf(parts[1].trim());
                    int           pA = Integer.parseInt(parts[2].trim());
                    int           pB = Integer.parseInt(parts[3].trim());
                    records.put(makeKey(a, b), new MatchupRecord(a, b, pA, pB));
                } catch (IllegalArgumentException ignored) {
                    // Unknown variant name or malformed int — skip line
                }
            }
        } catch (IOException e) {
            System.err.println("Scoreboard: could not load — " + e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Returns the canonical key: alphabetically sorted pair. */
    private static String makeKey(PlayerVariant a, PlayerVariant b) {
        PlayerVariant[] s = sorted(a, b);
        return s[0].name() + "," + s[1].name();
    }

    /** Returns [a, b] sorted alphabetically by enum name. */
    private static PlayerVariant[] sorted(PlayerVariant a, PlayerVariant b) {
        return a.name().compareTo(b.name()) <= 0
                ? new PlayerVariant[]{a, b}
                : new PlayerVariant[]{b, a};
    }

}
