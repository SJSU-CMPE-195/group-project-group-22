package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.annotations.GeneratedExcludeFromCoverage;
import edu.sjsu.spring2026.group32.pong.core.PongScoreboardService;
import edu.sjsu.spring2026.group32.pong.model.PlayerVariant;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * UI-facing scoreboard wrapper for Pong.
 */
public class Scoreboard {
    static final String FILE_NAME = "pong_scoreboard.txt";

    /**
     * Accumulated points for a single head-to-head matchup.
     */
    public static class MatchupRecord {
        public final PlayerVariant variantA;
        public final PlayerVariant variantB;
        public final int pointsA;
        public final int pointsB;

        MatchupRecord(PlayerVariant variantA, PlayerVariant variantB, int pointsA, int pointsB) {
            this.variantA = variantA;
            this.variantB = variantB;
            this.pointsA = pointsA;
            this.pointsB = pointsB;
        }
    }

    private final PongScoreboardService scoreboardService;

    /**
     * Default constructor: saves to {@value #FILE_NAME} in the working directory.
     */
    public Scoreboard() {
        this(Paths.get(FILE_NAME));
    }

    /**
     * Package-private constructor for testing with a custom path.
     */
    Scoreboard(Path filePath) {
        this.scoreboardService = new PongScoreboardService(filePath);
    }

    /**
     * Records one point scored by {@code winner} against {@code loser}.
     * Silently ignores self-matchups ({@code winner == loser}).
     */
    public void recordPoint(PlayerVariant winner, PlayerVariant loser) {
        scoreboardService.recordPoint(winner, loser);
    }

    /**
     * Clears all records from memory and disk.
     */
    public void reset() {
        scoreboardService.reset();
    }

    /**
     * Returns an unmodifiable view of all matchup records.
     */
    public Collection<MatchupRecord> getRecords() {
        List<MatchupRecord> records = new ArrayList<>();
        for (PongScoreboardService.MatchupRecord record : scoreboardService.getRecords()) {
            records.add(new MatchupRecord(
                    record.variantA(),
                    record.variantB(),
                    record.pointsA(),
                    record.pointsB()));
        }
        return Collections.unmodifiableList(records);
    }

    /**
     * Creates and returns a modal popup dialog showing all matchup records.
     *
     * @param owner parent frame for centering; may be {@code null}
     */
    @GeneratedExcludeFromCoverage
    public JDialog createPopupDialog(JFrame owner) {
        JDialog dialog = new JDialog(owner, "Pong Scoreboard", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        String[] cols = {"Player A", "Player B", "A Points", "B Points"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (MatchupRecord rec : getRecords()) {
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

        JButton resetBtn = new JButton("Reset All Scores");
        resetBtn.setForeground(new Color(180, 20, 20));
        resetBtn.setFocusPainted(false);
        resetBtn.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                    dialog,
                    "Clear all scores? This cannot be undone.",
                    "Confirm Reset",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                reset();
                while (model.getRowCount() > 0) {
                    model.removeRow(0);
                }
            }
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> dialog.dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        btnRow.add(resetBtn);
        btnRow.add(closeBtn);

        JLabel heading = new JLabel(
                "Head-to-head points per matchup  (grouped by variant pair)");
        heading.setFont(new Font("SansSerif", Font.PLAIN, 12));
        heading.setForeground(Color.DARK_GRAY);

        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(10, 10, 6, 10));
        root.add(heading, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(btnRow, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        return dialog;
    }
}
