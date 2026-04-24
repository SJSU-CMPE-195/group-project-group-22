package edu.sjsu.spring2026.group32.bidirectionaltest;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

final class VoltageGraphPanel extends JPanel {
    private static final int NUM_CHANNELS = 2;
    private static final int BUFFER = 500;
    private static final double V_REF = 3.3;
    private static final int ML = 44;
    private static final int MR = 10;
    private static final int MT = 12;
    private static final int MB = 26;

    private static final Color BG = new Color(18, 18, 18);
    private static final Color GRID_COLOR = new Color(45, 45, 45);
    private static final Color AXIS_TEXT = new Color(150, 150, 150);
    private static final Color HOVER_COLOR = new Color(255, 255, 255, 160);
    private static final Color TOOLTIP_BG = new Color(30, 30, 30, 220);

    private static final Color[] TRACE_COLORS = {
            new Color(50, 220, 80),
            new Color(80, 180, 255)
    };

    private final double[][] voltageBuffers = new double[NUM_CHANNELS][BUFFER];
    private final int[][] rawBuffers = new int[NUM_CHANNELS][BUFFER];
    private final long[][] millisBuffers = new long[NUM_CHANNELS][BUFFER];
    private final String[][] timeBuffers = new String[NUM_CHANNELS][BUFFER];
    private final int[] heads = new int[NUM_CHANNELS];
    private final int[] counts = new int[NUM_CHANNELS];
    private final boolean[] visible = {true, false};
    private final boolean[] injecting = {false, false};
    private final double[] injectionVoltage = {0.0, 0.0};

    private boolean paused;
    private int hoverX = -1;
    private final JButton pauseButton;

    VoltageGraphPanel() {
        setLayout(null);

        pauseButton = new JButton("Pause");
        pauseButton.setToolTipText("Pause graph updates");
        pauseButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        pauseButton.setMargin(new Insets(1, 5, 1, 5));
        pauseButton.setFocusPainted(false);
        pauseButton.addActionListener(e -> {
            paused = !paused;
            pauseButton.setText(paused ? "Resume" : "Pause");
            pauseButton.setToolTipText(paused ? "Resume graph" : "Pause graph updates");
            repaint();
        });
        add(pauseButton);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hoverX = e.getX();
                repaint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = -1;
                repaint();
            }
        });
    }

    void addSample(int channel, double volts, int raw, long millis, String time) {
        if (paused || channel < 0 || channel >= NUM_CHANNELS) {
            return;
        }

        int head = heads[channel];
        voltageBuffers[channel][head] = volts;
        rawBuffers[channel][head] = raw;
        millisBuffers[channel][head] = millis;
        timeBuffers[channel][head] = time;
        heads[channel] = (head + 1) % BUFFER;
        if (counts[channel] < BUFFER) {
            counts[channel]++;
        }
        repaint();
    }

    void setChannelVisible(int channel, boolean isVisible) {
        if (channel >= 0 && channel < NUM_CHANNELS) {
            visible[channel] = isVisible;
            repaint();
        }
    }

    void setInjection(int channel, boolean active, double volts) {
        if (channel >= 0 && channel < NUM_CHANNELS) {
            injecting[channel] = active;
            injectionVoltage[channel] = volts;
            repaint();
        }
    }

    void reset() {
        for (int channel = 0; channel < NUM_CHANNELS; channel++) {
            heads[channel] = 0;
            counts[channel] = 0;
            injecting[channel] = false;
            injectionVoltage[channel] = 0.0;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int plotWidth = width - ML - MR;
        int plotHeight = height - MT - MB;

        Dimension buttonSize = pauseButton.getPreferredSize();
        pauseButton.setBounds(width - buttonSize.width - MR, MT, buttonSize.width, buttonSize.height);

        g2.setColor(BG);
        g2.fillRect(0, 0, width, height);
        if (plotWidth <= 0 || plotHeight <= 0) {
            g2.dispose();
            return;
        }

        drawGrid(g2, plotWidth, plotHeight);
        drawInjectionLines(g2, plotWidth, plotHeight);

        HoverState hoverState = drawTraces(g2, plotWidth, plotHeight);

        if (!hoverState.anyData()) {
            g2.setColor(AXIS_TEXT);
            g2.setFont(new Font("SansSerif", Font.ITALIC, 12));
            String message = "Waiting for data...";
            FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(message, ML + (plotWidth - metrics.stringWidth(message)) / 2, MT + plotHeight / 2);
        }

        if (paused) {
            g2.setColor(new Color(255, 200, 0, 55));
            g2.fillRect(ML, MT, plotWidth, plotHeight);
            g2.setColor(new Color(255, 200, 0, 180));
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            String message = "PAUSED";
            FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(message, ML + (plotWidth - metrics.stringWidth(message)) / 2, MT + plotHeight / 2);
        }

        if (hoverState.hoverIndex() >= 0) {
            drawHoverOverlay(g2, plotWidth, plotHeight, hoverState);
        }

        g2.setColor(AXIS_TEXT);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(ML, MT, plotWidth, plotHeight);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.drawString("<- time (newest on right, last " + BUFFER + " samples)", ML + 4, height - 7);
        g2.dispose();
    }

    private void drawGrid(Graphics2D g2, int plotWidth, int plotHeight) {
        double[] gridValues = {0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.3};
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        FontMetrics metrics = g2.getFontMetrics();
        for (double gridValue : gridValues) {
            int y = yPx(gridValue, plotHeight);
            g2.setColor(GRID_COLOR);
            g2.drawLine(ML, MT + y, ML + plotWidth, MT + y);
            String label = String.format("%.1f", gridValue);
            g2.setColor(AXIS_TEXT);
            g2.drawString(label, ML - metrics.stringWidth(label) - 3, MT + y + 4);
        }
    }

    private void drawInjectionLines(Graphics2D g2, int plotWidth, int plotHeight) {
        float[] dash = {7f, 4f};
        for (int channel = 0; channel < NUM_CHANNELS; channel++) {
            if (!injecting[channel]) {
                continue;
            }

            int y = MT + yPx(injectionVoltage[channel], plotHeight);
            g2.setColor(TRACE_COLORS[channel]);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.drawLine(ML, y, ML + plotWidth, y);
            g2.setStroke(new BasicStroke(1f));
            g2.drawString(String.format("inject Ch%d %.2fV", channel + 1, injectionVoltage[channel]),
                    ML + 4, y - 3);
        }
    }

    private HoverState drawTraces(Graphics2D g2, int plotWidth, int plotHeight) {
        int hoverIndex = -1;
        int hoverChannel = -1;
        int hoverPixel = hoverX;
        boolean anyData = false;

        for (int channel = 0; channel < NUM_CHANNELS; channel++) {
            if (!visible[channel] || counts[channel] < 2) {
                continue;
            }

            anyData = true;
            int points = Math.min(counts[channel], plotWidth);
            int oldest = (heads[channel] - points + BUFFER) % BUFFER;

            g2.setColor(TRACE_COLORS[channel]);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int prevX = -1;
            int prevY = -1;
            for (int i = 0; i < points; i++) {
                int index = (oldest + i) % BUFFER;
                double volts = Math.max(0.0, Math.min(V_REF, voltageBuffers[channel][index]));
                int x = ML + (int) Math.round((double) i / (points - 1) * plotWidth);
                int y = MT + yPx(volts, plotHeight);

                if (hoverX >= ML && hoverX <= ML + plotWidth) {
                    if (hoverIndex < 0 || Math.abs(x - hoverX) < Math.abs(hoverPixel - hoverX)) {
                        hoverIndex = index;
                        hoverChannel = channel;
                        hoverPixel = x;
                    }
                }

                if (prevX >= 0) {
                    g2.drawLine(prevX, prevY, x, y);
                }
                prevX = x;
                prevY = y;
            }
        }

        return new HoverState(hoverIndex, hoverChannel, hoverPixel, anyData);
    }

    private void drawHoverOverlay(Graphics2D g2, int plotWidth, int plotHeight, HoverState hoverState) {
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(HOVER_COLOR);
        g2.drawLine(hoverState.hoverPixel(), MT, hoverState.hoverPixel(), MT + plotHeight);

        int referencePoints = Math.min(counts[hoverState.hoverChannel()], plotWidth);
        int referenceOldest = (heads[hoverState.hoverChannel()] - referencePoints + BUFFER) % BUFFER;
        int hoverOffset = (hoverState.hoverIndex() - referenceOldest + BUFFER) % BUFFER;

        for (int channel = 0; channel < NUM_CHANNELS; channel++) {
            if (!visible[channel] || counts[channel] < 2) {
                continue;
            }

            int points = Math.min(counts[channel], plotWidth);
            int oldest = (heads[channel] - points + BUFFER) % BUFFER;
            int safeOffset = Math.min(hoverOffset, points - 1);
            int index = (oldest + safeOffset) % BUFFER;
            double volts = Math.max(0.0, Math.min(V_REF, voltageBuffers[channel][index]));
            int dotY = MT + yPx(volts, plotHeight);

            g2.setColor(Color.WHITE);
            g2.fillOval(hoverState.hoverPixel() - 4, dotY - 4, 8, 8);
            g2.setColor(TRACE_COLORS[channel]);
            g2.fillOval(hoverState.hoverPixel() - 2, dotY - 2, 5, 5);
        }

        drawTooltip(g2, plotWidth, plotHeight, hoverState, hoverOffset);
    }

    private void drawTooltip(Graphics2D g2, int plotWidth, int plotHeight, HoverState hoverState, int hoverOffset) {
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        FontMetrics metrics = g2.getFontMetrics();

        String timestampLine = timeBuffers[hoverState.hoverChannel()][hoverState.hoverIndex()] != null
                ? timeBuffers[hoverState.hoverChannel()][hoverState.hoverIndex()]
                : "-";
        String millisLine = String.format("t = %d ms", millisBuffers[hoverState.hoverChannel()][hoverState.hoverIndex()]);

        java.util.List<String[]> channelLines = new java.util.ArrayList<>();
        for (int channel = 0; channel < NUM_CHANNELS; channel++) {
            if (!visible[channel] || counts[channel] < 2) {
                continue;
            }

            int points = Math.min(counts[channel], plotWidth);
            int oldest = (heads[channel] - points + BUFFER) % BUFFER;
            int safeOffset = Math.min(hoverOffset, points - 1);
            int index = (oldest + safeOffset) % BUFFER;
            double volts = Math.max(0.0, Math.min(V_REF, voltageBuffers[channel][index]));
            channelLines.add(new String[]{
                    String.format("Ch%d: %.3f V", channel + 1, volts),
                    String.format("raw %d", rawBuffers[channel][index])
            });
        }

        int lineHeight = metrics.getHeight();
        int rows = 2 + channelLines.size() * 2;
        int tooltipHeight = lineHeight * rows + 10;

        int tooltipWidth = Math.max(metrics.stringWidth(timestampLine), metrics.stringWidth(millisLine));
        for (String[] linePair : channelLines) {
            tooltipWidth = Math.max(tooltipWidth,
                    Math.max(metrics.stringWidth(linePair[0]), metrics.stringWidth(linePair[1])));
        }
        tooltipWidth += 14;

        int anchorY = MT + yPx(
                Math.max(0.0, Math.min(V_REF, voltageBuffers[hoverState.hoverChannel()][hoverState.hoverIndex()])),
                plotHeight);

        int tooltipX = hoverState.hoverPixel() + 10;
        int tooltipY = anchorY - tooltipHeight / 2;

        if (tooltipX + tooltipWidth > getWidth() - MR) {
            tooltipX = hoverState.hoverPixel() - tooltipWidth - 10;
        }
        if (tooltipY < MT + 2) {
            tooltipY = MT + 2;
        }
        if (tooltipY + tooltipHeight > MT + plotHeight - 2) {
            tooltipY = MT + plotHeight - tooltipHeight - 2;
        }

        g2.setColor(TOOLTIP_BG);
        g2.fillRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 7, 7);
        g2.setColor(HOVER_COLOR);
        g2.drawRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 7, 7);

        int row = 1;
        g2.setColor(Color.WHITE);
        g2.drawString(timestampLine, tooltipX + 7, tooltipY + lineHeight * row++);
        g2.setColor(AXIS_TEXT);
        g2.drawString(millisLine, tooltipX + 7, tooltipY + lineHeight * row++);

        for (int i = 0; i < channelLines.size(); i++) {
            g2.setColor(TRACE_COLORS[channelLines.size() == 1 ? (visible[0] ? 0 : 1) : i]);
            g2.drawString(channelLines.get(i)[0], tooltipX + 7, tooltipY + lineHeight * row++);
            g2.setColor(AXIS_TEXT);
            g2.drawString(channelLines.get(i)[1], tooltipX + 7, tooltipY + lineHeight * row++);
        }
    }

    private static int yPx(double volts, int plotHeight) {
        return plotHeight - (int) Math.round(Math.min(volts, V_REF) / V_REF * plotHeight);
    }

    private record HoverState(int hoverIndex, int hoverChannel, int hoverPixel, boolean anyData) {}
}
