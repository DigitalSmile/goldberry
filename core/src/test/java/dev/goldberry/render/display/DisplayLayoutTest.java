package dev.goldberry.render.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;

/// Where a window opens and how it is clamped back onto a display — the rule
/// that turns "the monitor it was on has gone" into "it opens on the screen".
class DisplayLayoutTest {

    /// A laptop at the origin with a 40-pixel panel at the bottom, and a
    /// monitor to its right.
    private static final Display LAPTOP = new Display(
            1,
            "Built-in",
            LogicalRect.of(0, 0, 1920, 1080),
            LogicalRect.of(0, 0, 1920, 1040),
            new DisplayScale(1.25f),
            true);

    private static final Display MONITOR = new Display(
            2,
            "DELL U2720Q",
            LogicalRect.of(1920, 0, 2560, 1440),
            LogicalRect.of(1920, 0, 2560, 1440),
            DisplayScale.ONE,
            false);

    private static final DisplayLayout TWO = new DisplayLayout(List.of(LAPTOP, MONITOR));

    private static final LogicalSize WINDOW = LogicalSize.of(800, 600);

    @Test
    @DisplayName("a window is on the display that holds most of it")
    void underTheLargestOverlap() {
        assertEquals(Optional.of(MONITOR), TWO.under(new LogicalRect(new LogicalPoint(1800, 100), WINDOW)));
        assertEquals(Optional.of(LAPTOP), TWO.under(new LogicalRect(new LogicalPoint(1500, 100), WINDOW)));
        assertEquals(Optional.empty(), TWO.under(new LogicalRect(new LogicalPoint(-2000, 0), WINDOW)));
    }

    @Test
    @DisplayName("a window on no display is nearest the display whose edge is closest to its centre")
    void nearestWhenOnNone() {
        assertEquals(Optional.of(LAPTOP), TWO.nearest(new LogicalRect(new LogicalPoint(-2000, 0), WINDOW)));
        assertEquals(Optional.of(MONITOR), TWO.nearest(new LogicalRect(new LogicalPoint(6000, 200), WINDOW)));
    }

    @Test
    @DisplayName("a clamp pulls a window wholly into its display's usable bounds, clear of the panel")
    void clampsIntoTheUsableBounds() {
        // Hanging off the bottom of the laptop, over its panel.
        assertEquals(new LogicalPoint(100, 440), TWO.clamp(new LogicalRect(new LogicalPoint(100, 900), WINDOW)));
        // Straddling the two, mostly on the monitor: pulled onto the monitor.
        assertEquals(new LogicalPoint(1920, 100), TWO.clamp(new LogicalRect(new LogicalPoint(1800, 100), WINDOW)));
        // Already inside: left alone.
        assertEquals(new LogicalPoint(200, 200), TWO.clamp(new LogicalRect(new LogicalPoint(200, 200), WINDOW)));
    }

    @Test
    @DisplayName("a window bigger than its display keeps its top-left corner, and so its title bar, on it")
    void tooBigKeepsTheCorner() {
        var huge = new LogicalRect(new LogicalPoint(-300, -200), LogicalSize.of(3000, 2000));

        assertEquals(new LogicalPoint(0, 0), TWO.clamp(huge));
    }

    @Test
    @DisplayName("a remembered position on a display that exists opens there")
    void opensWhereItWas() {
        assertEquals(
                Optional.of(new LogicalPoint(2100, 300)),
                TWO.opening(WINDOW, new LogicalPoint(2100, 300), "DELL U2720Q"));
    }

    @Test
    @DisplayName("a position on a monitor that has gone opens centred on the display it was named for")
    void fallsBackToTheNamedDisplay() {
        var laptopOnly = new DisplayLayout(List.of(LAPTOP));

        assertEquals(
                Optional.of(new LogicalPoint(560, 220)),
                laptopOnly.opening(WINDOW, new LogicalPoint(2100, 300), "Built-in"));
    }

    @Test
    @DisplayName("a position on a monitor that has gone, with no name that exists, opens centred on the primary")
    void fallsBackToThePrimary() {
        var laptopOnly = new DisplayLayout(List.of(LAPTOP));

        assertEquals(
                Optional.of(new LogicalPoint(560, 220)),
                laptopOnly.opening(WINDOW, new LogicalPoint(2100, 300), "DELL U2720Q"));
    }

    @Test
    @DisplayName("a display named with no position opens centred on it")
    void centredOnTheNamedDisplay() {
        assertEquals(Optional.of(new LogicalPoint(2800, 420)), TWO.opening(WINDOW, null, "DELL U2720Q"));
    }

    @Test
    @DisplayName("nothing asked, or an unknown display alone, is left to the platform")
    void leftToThePlatform() {
        assertEquals(Optional.empty(), TWO.opening(WINDOW, null, null));
        assertEquals(Optional.empty(), TWO.opening(WINDOW, null, "a projector"));
    }

    @Test
    @DisplayName("with no displays to check against, a position is taken as it is")
    void noDisplaysClampNothing() {
        var point = new LogicalPoint(-5000, 7000);

        assertEquals(Optional.of(point), DisplayLayout.NONE.opening(WINDOW, point, null));
        assertEquals(point, DisplayLayout.NONE.clamp(new LogicalRect(point, WINDOW)));
        assertTrue(DisplayLayout.NONE.primary().isEmpty());
    }

    @Test
    @DisplayName("the primary is the display marked so, and a name finds the first display that has it")
    void primaryAndNamed() {
        var flipped = new DisplayLayout(List.of(MONITOR, LAPTOP));

        assertEquals(Optional.of(LAPTOP), flipped.primary());
        assertEquals(Optional.of(MONITOR), flipped.named("DELL U2720Q"));
        assertEquals(Optional.of(LAPTOP), flipped.byId(1));
    }
}
