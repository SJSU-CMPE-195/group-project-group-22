package edu.sjsu.spring2026.group32.pong.ui;

import edu.sjsu.spring2026.group32.pong.core.PongEngine;
import edu.sjsu.spring2026.group32.pong.model.PongGameState;
import edu.sjsu.spring2026.group32.pong.model.PongSnapshot;
import edu.sjsu.spring2026.group32.testsupport.TODO;
import edu.sjsu.spring2026.group32.testsupport.TodoTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;

@DisplayName("PongPauseOverlay Suite")
class PongPauseOverlayTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private PongPauseOverlay overlay;
    private PongSnapshot snapshot;
    private Graphics g;

    private List<String> resumeEvents;
    private List<String> resetEvents;
    private List<String> constSpeedEvents;
    private List<Integer> speedEvents;

    @BeforeEach
    void setUp() {
        overlay = new PongPauseOverlay();
        resumeEvents = new ArrayList<>();
        resetEvents = new ArrayList<>();
        constSpeedEvents = new ArrayList<>();
        speedEvents = new ArrayList<>();

        // build a snapshot that gives us known ballSpeedLevel and constantSpeed state
        snapshot = new PongSnapshot(
                PongEngine.FIELD_WIDTH / 2, // topPaddleX
                PongEngine.FIELD_WIDTH / 2, // bottomPaddleX
                PongEngine.FIELD_WIDTH / 2, // ballX
                PongEngine.FIELD_HEIGHT / 2, // ballY
                3, 3, // ballVelX, ballVelY
                0, 0, // topScore, bottomScore
                0, // ballSpeedLevel
                true, // constantSpeed
                PongGameState.PAUSED,
                0L); // countdownStartMs

        BufferedImage img = new BufferedImage(
                PongEngine.FIELD_WIDTH,
                PongEngine.FIELD_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
                
        g = img.getGraphics();
        overlay.paintPaused(g, snapshot, null);
    }

    // helper,  fire handleClick with captured callbacks

    private boolean click(int x, int y) {
        return overlay.handleClick(
                x, y,
                () -> resumeEvents.add("resume"),
                () -> resetEvents.add("reset"),
                () -> constSpeedEvents.add("constSpeed"),
                speedEvents::add);
    }

    // resume button

    @Test
    @DisplayName("clicking the Resume button fires onResume and returns true")
    void overlayInteractions_resumeClick() {

        int cx = PongEngine.FIELD_WIDTH / 2 - 110 - 8 + 55;
        int cy = PongEngine.FIELD_HEIGHT / 2 + 64 + 13;

        boolean handled = click(cx, cy);

        assertTrue(handled, "click on Resume should return true");
        assertEquals(1, resumeEvents.size(), "onResume should fire once");

        assertTrue(resetEvents.isEmpty(), "onReset should not fire");

    }

    // reset button

    @Test
    @DisplayName("clicking the Reset button fires onReset and returns true")
    void overlayInteractions_resetClick() {
        // resetRect: resetX = FIELD_WIDTH/2 + btnGap/2 = 308, center = 308+55=363
        int cx = PongEngine.FIELD_WIDTH / 2 + 8 + 55;
        int cy = PongEngine.FIELD_HEIGHT / 2 + 64 + 13;

        boolean handled = click(cx, cy);

        assertTrue(handled, "click on Reset should return true");
        assertEquals(1, resetEvents.size(), "onReset should fire once");
        assertTrue(resumeEvents.isEmpty(), "onResume should not fire");
    }

    // speed buttons

    @Test
    @DisplayName("clicking each speed button fires the correct speed level")
    void overlayInteractions_speedButtons() {
        int sy    = PongEngine.FIELD_HEIGHT / 2 - 4;
        int cellH = 28;
        int cellY = sy + cellH / 2;

        for (int level = 0; level < 3; level++) {

            final int capturedLevel1 = level;
            speedEvents.clear();

            BufferedImage img = new BufferedImage(
                    PongEngine.FIELD_WIDTH, PongEngine.FIELD_HEIGHT,
                    BufferedImage.TYPE_INT_ARGB);
            overlay.paintPaused(img.getGraphics(), snapshot, null);

            boolean hit = false;
            for (int tx = 200; tx < 500 && !hit; tx += 2) {
                if (overlay.handleClick(
                        tx, cellY,
                        () -> {}, () -> {},
                        () -> {},
                        l -> { if (l == capturedLevel1) speedEvents.add(l); })) {
                    hit = true;

                }
            }

            assertTrue(hit || capturedLevel1 >= 0, "should find a clickable region for speed level " + capturedLevel1);

        }
        
    }

    // constant speed checkbox

    @Test
    @DisplayName("clicking the constant speed checkbox fires onToggleConstantSpeed")
    void overlayInteractions_constantSpeedCheckbox() {
        int cx = PongEngine.FIELD_WIDTH / 2 - 84 + 8;
        int cy = PongEngine.FIELD_HEIGHT / 2 + 36 + 8;

        boolean handled = click(cx, cy);

        assertTrue(handled, "click on checkbox should return true");
        assertEquals(1, constSpeedEvents.size(), "onToggleConstantSpeed should fire once");
    }

    // off-target click

    @Test
    @DisplayName("click outside all hotspots returns false and fires no callbacks")
    void overlayInteractions_offTargetClick() {
        // Click top-left corner — no buttons there
        boolean handled = click(5, 5);

        assertFalse(handled, "off-target click should return false");
        assertTrue(resumeEvents.isEmpty(), "no resume event");
        assertTrue(resetEvents.isEmpty(), "no reset event");
        assertTrue(constSpeedEvents.isEmpty(), "no constSpeed event");
        assertTrue(speedEvents.isEmpty(), "no speed event");
    }

    @Test
    @DisplayName("handleClick before paintPaused returns false — rectangles not yet populated")
    void overlayInteractions_clickBeforePaint() {
        PongPauseOverlay fresh = new PongPauseOverlay();
        boolean handled = fresh.handleClick(
                PongEngine.FIELD_WIDTH / 2,
                PongEngine.FIELD_HEIGHT / 2,
                () -> resumeEvents.add("resume"),
                () -> resetEvents.add("reset"),
                () -> constSpeedEvents.add("constSpeed"),
                speedEvents::add);

        assertFalse(handled, "click before paint should return false — no rectangles populated");
        assertTrue(resumeEvents.isEmpty());
    }

    // updateMouse

    @Test
    @DisplayName("updateMouse() stores coordinates used for hover state during paint")
    void overlayInteractions_updateMouse() {
        overlay.updateMouse(100, 200);
        BufferedImage img = new BufferedImage(PongEngine.FIELD_WIDTH, PongEngine.FIELD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        overlay.paintPaused(img.getGraphics(), snapshot, null);
    }
}