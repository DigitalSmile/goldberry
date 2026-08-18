package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.Placement.Align;
import io.github.digitalsmile.goldberry.Placement.Side;
import io.github.digitalsmile.goldberry.backend.LogicalPoint;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [Placement]'s three rules, one case each and then the corners.
///
/// All of it is arithmetic — no window, no display, no popup — which is the point
/// of it being a separate class: "the menu opened underneath the taskbar" is
/// otherwise a bug you can only find by having a taskbar.
class PlacementTest {

    /// A screen with 40px reserved at the bottom, which is what tells a work area
    /// apart from a display's full size.
    private static final LogicalRect SCREEN = LogicalRect.of(0, 0, 1920, 1040);

    private static LogicalRect anchor(float x, float y) {
        return LogicalRect.of(x, y, 100, 30);
    }

    private static final LogicalSize MENU = LogicalSize.of(180, 132);

    @Test
    @DisplayName("with room below, a dropdown opens below, left edges aligned")
    void preferredSide() {
        var placed = Placement.BELOW.place(anchor(200, 300), MENU, SCREEN);

        assertEquals(new LogicalPoint(200, 334), placed.at(), "anchor bottom 330 plus a 4px gap");
        assertEquals(Side.BOTTOM, placed.side());
        assertFalse(placed.flipped());
        assertFalse(placed.shifted());
    }

    /// Rule 2. The anchor's bottom is 1000; 1000 + 4 + 132 is past the work
    /// area's 1040, so it goes above instead.
    @Test
    @DisplayName("with no room below, it flips above")
    void flipsWhenItWillNotFit() {
        var placed = Placement.BELOW.place(anchor(200, 970), MENU, SCREEN);

        assertEquals(Side.TOP, placed.side());
        assertTrue(placed.flipped());
        assertEquals(970 - 4 - 132, placed.at().y());
    }

    /// The flip is a last resort, not an optimization: with room below it stays
    /// below even when there is *more* room above, because a menu that changed
    /// sides on the strength of a comparison is a menu nobody can predict.
    @Test
    @DisplayName("more room on the other side is not a reason to flip")
    void doesNotFlipForComfort() {
        var placed = Placement.BELOW.place(anchor(200, 800), MENU, SCREEN);

        assertEquals(Side.BOTTOM, placed.side());
        assertFalse(placed.flipped());
    }

    /// And when neither side fits — a menu taller than the screen — it stays on
    /// the side it was asked for rather than flipping to a side that is no better.
    @Test
    @DisplayName("when neither side fits, it does not flip")
    void neitherSideFits() {
        var tall = LogicalSize.of(180, 2000);
        var placed = Placement.BELOW.place(anchor(200, 500), tall, SCREEN);

        assertEquals(Side.BOTTOM, placed.side());
        assertFalse(placed.flipped());
    }

    /// Rule 3. The anchor is 60px from the right edge and the menu is 180 wide,
    /// so it slides left until its right edge is on the work area's.
    @Test
    @DisplayName("near the right edge it shifts along, staying on the same side")
    void shiftsAlongTheCrossAxis() {
        var placed = Placement.BELOW.place(anchor(1860, 300), MENU, SCREEN);

        assertEquals(Side.BOTTOM, placed.side(), "shifting does not change sides");
        assertTrue(placed.shifted());
        assertEquals(1920 - 180, placed.at().x());
        assertEquals(334, placed.at().y(), "and it is still under its anchor");
    }

    @Test
    @DisplayName("and at the left edge it shifts the other way")
    void shiftsAtTheLeftEdge() {
        var placed = new Placement(Side.BOTTOM, Align.END, 4)
                .place(anchor(0, 300), MENU, SCREEN);

        assertEquals(0f, placed.at().x(), "aligned to the anchor's right edge would be -80");
        assertTrue(placed.shifted());
    }

    /// A popup wider than the screen loses its far end, not its near one: the top
    /// of a too-long menu is what a reader needs.
    @Test
    @DisplayName("a popup too big to fit is clamped to the near edge")
    void clampsToTheNearEdge() {
        var huge = LogicalSize.of(3000, 132);
        var placed = Placement.BELOW.place(anchor(900, 300), huge, SCREEN);

        assertEquals(0f, placed.at().x());
        assertTrue(placed.shifted());
    }

    @Test
    @DisplayName("centre and end alignment line up with the anchor, not the screen")
    void alignment() {
        var a = anchor(500, 300);

        assertEquals(500f, Placement.BELOW.place(a, MENU, SCREEN).at().x());
        assertEquals(500 + (100 - 180) / 2f,
                Placement.BELOW.align(Align.CENTER).place(a, MENU, SCREEN).at().x());
        assertEquals(500 + 100 - 180,
                Placement.BELOW.align(Align.END).place(a, MENU, SCREEN).at().x());
    }

    /// A submenu: to the end side, top edges together, no gap.
    @Test
    @DisplayName("a submenu opens to the end side and flips to the start one at the edge")
    void submenu() {
        var placed = Placement.AFTER.place(anchor(200, 300), MENU, SCREEN);
        assertEquals(new LogicalPoint(300, 300), placed.at());
        assertEquals(Side.END, placed.side());

        var atTheEdge = Placement.AFTER.place(anchor(1800, 300), MENU, SCREEN);
        assertEquals(Side.START, atTheEdge.side());
        assertTrue(atTheEdge.flipped());
        assertEquals(1800 - 180, atTheEdge.at().x());
    }

    /// The work area is not the display: 40px are reserved at the bottom of
    /// `SCREEN`, and a placement that used the display's full height would put
    /// this menu underneath a taskbar.
    @Test
    @DisplayName("the reserved strip is respected, which is the whole reason for a work area")
    void respectsTheReservedStrip() {
        var justAboveTheTaskbar = anchor(200, 900);
        var placed = Placement.BELOW.place(justAboveTheTaskbar, LogicalSize.of(180, 120), SCREEN);

        assertEquals(Side.TOP, placed.side(),
                "930 + 4 + 120 is 1054, which is inside a 1080 display and outside a 1040"
                        + " work area");
    }

    @Test
    @DisplayName("the gap is a gap, and zero is legal")
    void gap() {
        assertEquals(330f, Placement.BELOW.gap(0).place(anchor(200, 300), MENU, SCREEN).at().y());
        assertEquals(Side.TOP, Side.BOTTOM.opposite());
        assertEquals(Side.START, Side.END.opposite());
        assertTrue(Side.TOP.isVertical());
        assertFalse(Side.END.isVertical());
    }
}
