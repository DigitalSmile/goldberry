package dev.goldberry.paint.slice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.image.BorderImage.Repeat;
import dev.goldberry.css.image.NineSlice;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.render.model.PhysicalRect;

/// A painter's nine-slice, checked by numbers: which rectangle of the picture
/// goes where on the rectangle a painter names.
class NinePatchTest {

    private static final double EPSILON = 1e-9;

    private static void assertPlaced(NineSlice.Piece piece, double x, double y, double width, double height) {
        assertEquals(x, piece.x(), EPSILON, "x");
        assertEquals(y, piece.y(), EPSILON, "y");
        assertEquals(width, piece.width(), EPSILON, "width");
        assertEquals(height, piece.height(), EPSILON, "height");
    }

    @Test
    @DisplayName("a plate cut 22 64 22 64 keeps its ends whole, shrunk alike when they do not fit")
    void namePlate() {
        // The Gwent clone's name plate: 995 x 113, drawn as a 300 x 40 plate. The
        // slices are 44 pixels tall together and the plate is 40, so every side
        // shrinks by 40/44, and the corners keep their shape.
        var pieces = NinePatch.of(22, 64, 22, 64).pieces(995, 113, 300, 40);

        // The two rows of corners fill the height, so the side edges and the
        // middle have nothing left to fill and are left out.
        assertEquals(6, pieces.size(), "four corners and the top and bottom edges");
        var factor = 40.0 / 44;
        var corner = pieces.getFirst();
        assertEquals(new PhysicalRect(0, 0, 64, 22), corner.source());
        assertPlaced(corner, 0, 0, 64 * factor, 22 * factor);
        var topRight = pieces.get(1);
        assertEquals(new PhysicalRect(931, 0, 64, 22), topRight.source());
        assertPlaced(topRight, 300 - 64 * factor, 0, 64 * factor, 22 * factor);
        var bottomLeft = pieces.get(2);
        assertEquals(new PhysicalRect(0, 91, 64, 22), bottomLeft.source());
        assertPlaced(bottomLeft, 0, 20, 64 * factor, 22 * factor);
    }

    @Test
    @DisplayName("the same plate with its sides drawn narrower keeps a middle band")
    void namePlateWithWidths() {
        var pieces = NinePatch.of(22, 64, 22, 64).withWidths(8, 24, 8, 24).pieces(995, 113, 300, 40);

        assertEquals(9, pieces.size(), "four corners, four edges and the middle");
        assertPlaced(pieces.getFirst(), 0, 0, 24, 8);
        var middle = pieces.getLast();
        assertEquals(new PhysicalRect(64, 22, 867, 69), middle.source());
        assertPlaced(middle, 24, 8, 252, 24);
    }

    @Test
    @DisplayName("corners at their own size, edges stretched between them")
    void stretched() {
        var pieces = NinePatch.of(10, 10, 10, 10).pieces(30, 30, 100, 60);

        assertPlaced(pieces.get(0), 0, 0, 10, 10);
        assertPlaced(pieces.get(3), 90, 50, 10, 10);
        // The top edge.
        assertEquals(new PhysicalRect(10, 0, 10, 10), pieces.get(4).source());
        assertPlaced(pieces.get(4), 10, 0, 80, 10);
        assertTrue(pieces.get(4).isStretched());
        assertPlaced(pieces.getLast(), 10, 10, 80, 40);
    }

    @Test
    @DisplayName("widths in logical pixels scale the corners to them")
    void widths() {
        var pieces = NinePatch.of(10, 10, 10, 10).withWidths(5, 5, 5, 5).pieces(30, 30, 100, 60);

        assertPlaced(pieces.getFirst(), 0, 0, 5, 5);
        assertPlaced(pieces.getLast(), 5, 5, 90, 50);
    }

    @Test
    @DisplayName("a percentage width is of the rectangle, and auto is the slice's own size")
    void percentAndAuto() {
        var widths = new Insets(Length.percent(50), Length.AUTO, Length.AUTO, Length.points(4));
        var pieces = NinePatch.of(10, 10, 10, 10).withWidths(widths).pieces(30, 30, 100, 40);

        // 50% of 40 is 20, but with the bottom's 10 that is 30 of 40: it fits.
        assertPlaced(pieces.getFirst(), 0, 0, 4, 20);
        assertPlaced(pieces.get(3), 90, 30, 10, 10);
    }

    @Test
    @DisplayName("without fill the middle is left out")
    void noFill() {
        var pieces = NinePatch.of(10, 10, 10, 10).withFill(false).pieces(30, 30, 100, 60);

        assertEquals(8, pieces.size());
    }

    @Test
    @DisplayName("a 2x picture is cut at twice the numbers and drawn at the same size")
    void density() {
        var pieces = NinePatch.of(10, 10, 10, 10).atDensity(2).pieces(60, 60, 100, 60);

        assertEquals(new PhysicalRect(0, 0, 20, 20), pieces.getFirst().source());
        assertPlaced(pieces.getFirst(), 0, 0, 10, 10);
    }

    @Test
    @DisplayName("a repeated edge is tiled at its own scale, a round one a whole number of times")
    void repeat() {
        var repeated = NinePatch.of(10, 10, 10, 10)
                .withRepeat(Repeat.REPEAT, Repeat.STRETCH)
                .pieces(30, 30, 100, 60);
        var top = repeated.get(4);
        assertFalse(top.isStretched());
        assertEquals(10, top.tileWidth(), EPSILON);

        var rounded = NinePatch.of(10, 10, 10, 10)
                .withRepeat(Repeat.ROUND, Repeat.ROUND)
                .pieces(30, 30, 105, 60);
        // 85 across the top is 8.5 tiles, rounded to 9.
        assertEquals(85.0 / 9, rounded.get(4).tileWidth(), EPSILON);
    }

    @Test
    @DisplayName("a negative slice, an undefined width and no density are refused")
    void refused() {
        assertThrows(IllegalArgumentException.class, () -> NinePatch.of(-1, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> NinePatch.of(1, 1, 1, 1).withWidths(Insets.all(Length.UNDEFINED)));
        assertThrows(
                IllegalArgumentException.class, () -> NinePatch.of(1, 1, 1, 1).withWidths(-1, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> NinePatch.of(1, 1, 1, 1).atDensity(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NinePatch(
                        Insets.all(Length.AUTO), Insets.all(Length.AUTO), true, Repeat.STRETCH, Repeat.STRETCH, 1));
    }
}
