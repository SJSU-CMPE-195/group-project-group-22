package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.pong.PlayerVariant;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Horizontal toolbar rendered above (TOP) or below (BOTTOM) the Pong canvas.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Show a {@link PlayerVariant} dropdown for this side.</li>
 *   <li>Prevent selecting the variant that is already in use on the other side
 *       (the renderer grays it out; the action listener silently reverts the
 *       selection if the user somehow picks it).</li>
 *   <li>Gray out {@link PlayerVariant#HARDWARE} when no hardware player is
 *       available (constructor parameter {@code hardwareAvailable}).</li>
 *   <li>Provide a "Scoreboard" button that opens
 *       {@link Scoreboard#createPopupDialog}.</li>
 *   <li>Lock itself (disable dropdown) during PLAYING state; unlock during
 *       PAUSED state via {@link #setSelectionLocked(boolean)}.</li>
 * </ul>
 *
 * <h3>Owner-frame resolution</h3>
 * The parent JFrame for the scoreboard popup is resolved lazily via
 * {@link SwingUtilities#getWindowAncestor} so the toolbar does not need
 * a frame reference at construction time.
 */
public class PongToolbar extends JPanel {

    /** Which edge of the game field this toolbar is attached to. */
    public enum Side { TOP, BOTTOM }

    // ─── Label configuration — edit here to change text or color ─────────────

    /** Format string for the per-channel spike label.  Args: ch1 count, ch2 count. */
    private static final String CHANNEL_SPIKE_FMT   = "Ch1 Spikes: %d  Ch2 Spikes: %d";
    private static final Color  CHANNEL_SPIKE_COLOR  = new Color(100, 255, 150);

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final Scoreboard scoreboard;
    private final boolean    hardwareAvailable;

    final JComboBox<PlayerVariant> dropdown;
    private PlayerVariant lastValidSelection;
    private PlayerVariant lockedOutVariant;     // the other side's current choice
    private Consumer<PlayerVariant> onVariantChanged;

    /** Label shown in the toolbar when the HARDWARE variant is active. */
    private final JLabel channelSpikeLabel;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * @param side              which edge (TOP or BOTTOM)
     * @param allowedVariants   the variants shown in this toolbar's dropdown;
     *                          pass only the variants valid for this side
     *                          (e.g. TOP omits HUMAN, BOTTOM omits HARDWARE)
     * @param initial           variant pre-selected when the toolbar is first shown
     * @param hardwareAvailable {@code false} grays out the HARDWARE option
     * @param scoreboard        shared scoreboard instance for popup access
     */
    public PongToolbar(Side side,
                       PlayerVariant[] allowedVariants,
                       PlayerVariant initial,
                       boolean hardwareAvailable,
                       Scoreboard scoreboard) {
        this.scoreboard        = scoreboard;
        this.hardwareAvailable = hardwareAvailable;
        this.lastValidSelection = initial;

        setLayout(new FlowLayout(
                side == Side.TOP ? FlowLayout.LEFT : FlowLayout.RIGHT, 10, 5));
        setBackground(new Color(28, 28, 28));
        setBorder(BorderFactory.createMatteBorder(
                side == Side.TOP ? 0 : 1, 0, side == Side.TOP ? 1 : 0, 0,
                new Color(60, 60, 60)));

        // ── Side label ───────────────────────────────────────────────────────
        JLabel sideLabel = new JLabel(side == Side.TOP ? "TOP ▶" : "BOTTOM ▶");
        sideLabel.setForeground(new Color(160, 160, 160));
        sideLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        add(sideLabel);

        // ── Player variant dropdown ───────────────────────────────────────────
        dropdown = new JComboBox<>(allowedVariants);
        dropdown.setSelectedItem(initial);
        dropdown.setRenderer(new VariantCellRenderer());
        dropdown.setPreferredSize(new Dimension(148, 26));
        dropdown.setFocusable(false);
        dropdown.addActionListener(e -> handleSelection());
        add(dropdown);

        // ── Scoreboard button ─────────────────────────────────────────────────
        JButton sbButton = new JButton("Scoreboard");
        sbButton.setFocusPainted(false);
        sbButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sbButton.addActionListener(e -> openScoreboard());
        add(sbButton);

        // ── Hardware event counters (hidden until HARDWARE variant is active) ──
        channelSpikeLabel = makeCountLabel(String.format(CHANNEL_SPIKE_FMT, 0, 0), CHANNEL_SPIKE_COLOR);
        add(channelSpikeLabel);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private static JLabel makeCountLabel(String text, Color fg) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(fg);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 12));
        lbl.setVisible(false);
        return lbl;
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Registers the callback fired whenever the user picks a valid new variant.
     * Called from the Swing EDT.
     */
    public void setOnVariantChanged(Consumer<PlayerVariant> listener) {
        this.onVariantChanged = listener;
    }

    /** Returns the currently displayed (and valid) selection. */
    public PlayerVariant getSelectedVariant() {
        return lastValidSelection;
    }

    /**
     * Tells this toolbar which variant the <em>other</em> side has chosen so it
     * can gray out that option in the dropdown.
     *
     * @param otherVariant the other toolbar's current selection
     */
    public void setLockedOutVariant(PlayerVariant otherVariant) {
        this.lockedOutVariant = otherVariant;
        dropdown.repaint();
    }

    /**
     * Locks or unlocks the dropdown.
     *
     * <ul>
     *   <li>{@code true}  → locked (PLAYING state)  — dropdown disabled</li>
     *   <li>{@code false} → unlocked (PAUSED state) — dropdown enabled</li>
     * </ul>
     */
    public void setSelectionLocked(boolean locked) {
        dropdown.setEnabled(!locked);
    }

    /**
     * Updates the per-channel spike label.
     * Must be called on the Swing EDT.
     *
     * @param ch1  Channel 1 spike events since the last ball hit/miss
     * @param ch2  Channel 2 spike events since the last ball hit/miss
     */
    public void setEventCounts(int ch1, int ch2) {
        channelSpikeLabel.setText(String.format(CHANNEL_SPIKE_FMT, ch1, ch2));
    }

    /**
     * Shows or hides the hardware event counter labels.
     * Safe to call from any thread.
     *
     * @param visible {@code true} when HARDWARE variant is active
     */
    public void setHardwareCountsVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> channelSpikeLabel.setVisible(visible));
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    /** Called by the dropdown action listener. */
    private void handleSelection() {
        PlayerVariant chosen = (PlayerVariant) dropdown.getSelectedItem();
        if (chosen == null) return;

        boolean unavailable = (chosen == lockedOutVariant)
                || (chosen == PlayerVariant.HARDWARE && !hardwareAvailable);

        if (unavailable) {
            // Silently revert without firing the callback
            dropdown.setSelectedItem(lastValidSelection);
            return;
        }

        if (chosen == lastValidSelection) return; // no real change

        lastValidSelection = chosen;
        if (onVariantChanged != null) onVariantChanged.accept(chosen);
    }

    /** Opens the scoreboard popup anchored to the nearest JFrame ancestor. */
    private void openScoreboard() {
        JFrame owner = (JFrame) SwingUtilities.getWindowAncestor(this);
        scoreboard.createPopupDialog(owner).setVisible(true);
    }

    // ─── Custom renderer ─────────────────────────────────────────────────────

    /**
     * Grays out variants that cannot currently be selected:
     * the other side's choice and HARDWARE when unavailable.
     */
    private class VariantCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof PlayerVariant v) {
                boolean unavailable = (v == lockedOutVariant)
                        || (v == PlayerVariant.HARDWARE && !hardwareAvailable);

                if (unavailable) {
                    setText(v.getDisplayName() + " (unavailable)");
                    setForeground(Color.GRAY);
                    setEnabled(false);
                } else {
                    setText(v.getDisplayName());
                    setForeground(isSelected ? Color.WHITE : Color.BLACK);
                    setEnabled(true);
                }
            }
            return this;
        }
    }
}
