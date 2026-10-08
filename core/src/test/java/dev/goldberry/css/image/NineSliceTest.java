package dev.goldberry.css.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Border;
import dev.goldberry.css.image.BorderImage.Extent;
import dev.goldberry.css.image.BorderImage.Repeat;
import dev.goldberry.css.image.BorderImage.Slice;
import dev.goldberry.layout.Length;
import dev.goldberry.render.model.PhysicalRect;

/// The nine-slice arithmetic: which rectangle of the picture goes where.
class NineSliceTest {

    private static final double EPSILON = 1e-9;

    /// A 144 × 144 panel sprite cut 48 in from every side, drawn 48 wide.
    private static BorderImage panel(boolean fill) {
        var cut = Length.points(48);
        return BorderImage.NONE
                .source(new CssImage.Url("panel.png"))
                .slice(new Slice(cut, cut, cut, cut, fill))
                .widths(List.of(px(48), px(48), px(48), px(48)));
    }

    private static Extent px(double value) {
        return new Extent(Extent.Kind.LENGTH, value);
    }

    private static void assertPlaced(NineSlice.Piece piece, double x, double y, double width, double height) {
        assertEquals(x, piece.x(), EPSILON, "x");
        assertEquals(y, piece.y(), EPSILON, "y");
        assertEquals(width, piece.width(), EPSILON, "width");
        assertEquals(height, piece.height(), EPSILON, "height");
    }

    @Test
    @DisplayName("corners keep their size, edges stretch between them, and the middle needs fill")
    void stretched() {
        var pieces = NineSlice.pieces(panel(false), 144, 144, 1, Border.NONE, 300, 200);

        assertEquals(8, pieces.size(), "no middle without fill");
        // Corners, top left first.
        assertEquals(new PhysicalRect(0, 0, 48, 48), pieces.get(0).source());
        assertPlaced(pieces.get(0), 0, 0, 48, 48);
        assertEquals(new PhysicalRect(96, 0, 48, 48), pieces.get(1).source());
        assertPlaced(pieces.get(1), 252, 0, 48, 48);
        assertPlaced(pieces.get(2), 0, 152, 48, 48);
        assertPlaced(pieces.get(3), 252, 152, 48, 48);
        // The top edge: the middle third of the sprite's top, stretched across.
        assertEquals(new PhysicalRect(48, 0, 48, 48), pieces.get(4).source());
        assertPlaced(pieces.get(4), 48, 0, 204, 48);
        assertTrue(pieces.get(4).isStretched());
        // The left edge, stretched down.
        assertEquals(new PhysicalRect(0, 48, 48, 48), pieces.get(6).source());
        assertPlaced(pieces.get(6), 0, 48, 48, 104);

        var filled = NineSlice.pieces(panel(true), 144, 144, 1, Border.NONE, 300, 200);
        assertEquals(9, filled.size());
        assertEquals(new PhysicalRect(48, 48, 48, 48), filled.getLast().source());
        assertPlaced(filled.getLast(), 48, 48, 204, 104);
    }

    @Test
    @DisplayName("a 2x picture is cut at twice the numbers and drawn at the same size")
    void doubleDensity() {
        var pieces = NineSlice.pieces(panel(true), 288, 288, 2, Border.NONE, 300, 200);

        assertEquals(new PhysicalRect(0, 0, 96, 96), pieces.getFirst().source());
        assertPlaced(pieces.getFirst(), 0, 0, 48, 48);
        assertEquals(new PhysicalRect(96, 96, 96, 96), pieces.getLast().source());
    }

    @Test
    @DisplayName("auto is the slice's size at the picture's density; a number is border widths")
    void widths() {
        var auto = panel(false).widths(List.of(Extent.AUTO, Extent.AUTO, Extent.AUTO, Extent.AUTO));
        assertPlaced(NineSlice.pieces(auto, 288, 288, 2, Border.NONE, 300, 200).getFirst(), 0, 0, 48, 48);

        var initial = panel(false).widths(BorderImage.NONE.widths());
        var pieces = NineSlice.pieces(initial, 144, 144, 1, Border.all(6, 0xFF000000), 300, 200);
        assertPlaced(pieces.getFirst(), 0, 0, 6, 6);

        var none = NineSlice.pieces(initial, 144, 144, 1, Border.NONE, 300, 200);
        assertEquals(List.of(), none, "the initial width of a box with no border is nothing to draw");
    }

    @Test
    @DisplayName("widths too wide for the box shrink together")
    void tooWide() {
        var pieces = NineSlice.pieces(panel(false), 144, 144, 1, Border.NONE, 60, 200);

        // 48 + 48 in 60 across: every width is scaled by 60 / 96.
        assertPlaced(pieces.getFirst(), 0, 0, 30, 30);
        assertPlaced(pieces.get(1), 30, 0, 30, 30);
    }

    @Test
    @DisplayName("outsets grow the area past the box")
    void outsets() {
        var image = panel(false).outsets(List.of(px(4), px(4), px(4), px(4)));

        var pieces = NineSlice.pieces(image, 144, 144, 1, Border.NONE, 100, 100);

        assertPlaced(pieces.getFirst(), -4, -4, 48, 48);
        assertPlaced(pieces.get(3), 56, 56, 48, 48);
        assertEquals(4, image.outsetsFor(Border.NONE)[0], EPSILON);
    }

    @Test
    @DisplayName("repeat tiles an edge at its own scale, centred; round fits a whole number")
    void tiles() {
        var repeat = panel(false).repeat(Repeat.REPEAT, Repeat.REPEAT);
        var top = NineSlice.pieces(repeat, 144, 144, 1, Border.NONE, 196, 196).get(4);

        // 100 across, 48-wide tiles: one centred at 26..74, the first from -22.
        assertPlaced(top, 48, 0, 100, 48);
        assertEquals(48, top.tileWidth(), EPSILON);
        assertEquals(-22, top.offsetX(), EPSILON);

        var round = panel(false).repeat(Repeat.ROUND, Repeat.ROUND);
        var rounded =
                NineSlice.pieces(round, 144, 144, 1, Border.NONE, 196, 196).get(4);
        // 100 / 48 is 2.08, so two tiles of 50.
        assertEquals(50, rounded.tileWidth(), EPSILON);
        assertEquals(0, rounded.offsetX(), EPSILON);
    }

    @Test
    @DisplayName("percentages are of the picture, and a slice past the middle leaves no middle")
    void percentages() {
        var half = Length.percent(50);
        var image = panel(true).slice(new Slice(half, half, half, half, true));

        var pieces = NineSlice.pieces(image, 100, 60, 1, Border.NONE, 300, 200);

        assertEquals(new PhysicalRect(0, 0, 50, 30), pieces.getFirst().source());
        assertEquals(4, pieces.size(), "four corners and nothing between them");
    }
}
