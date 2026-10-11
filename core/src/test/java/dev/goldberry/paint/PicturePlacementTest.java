package dev.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Corners;
import dev.goldberry.css.background.BackgroundPosition;
import dev.goldberry.css.background.BackgroundRepeat;
import dev.goldberry.css.background.BackgroundRepeat.Tiling;
import dev.goldberry.css.background.BackgroundSize;

/// Where a background picture's tiles go: a percentage of the room left,
/// `space` spreading whole tiles and `round` stretching them.
class PicturePlacementTest {

    private static final BackgroundArea AREA = new BackgroundArea(0, 0, 100, 40, Corners.SQUARE);

    private static PicturePainter.Placement place(BackgroundRepeat repeat, BackgroundPosition position) {
        return PicturePainter.Placement.of(BackgroundSize.AUTO, repeat, position, AREA, 30, 10);
    }

    @Test
    @DisplayName("`right bottom` puts the picture's corner on the area's")
    void percentages() {
        var placed = place(BackgroundRepeat.NO_REPEAT, new BackgroundPosition(0, 0, 100, 100));

        assertEquals(70, placed.x(), 1e-9);
        assertEquals(30, placed.y(), 1e-9);
        assertFalse(placed.across());
    }

    @Test
    @DisplayName("`space` fits whole tiles edge to edge and shares the rest between them")
    void space() {
        var placed = place(new BackgroundRepeat(Tiling.SPACE, Tiling.NO_REPEAT), BackgroundPosition.CENTRE);

        assertEquals(0, placed.x(), 1e-9, "the position is ignored");
        assertEquals(35, placed.stepX(), 1e-9, "three tiles of 30 and two gaps of 5");
        assertTrue(placed.across());
        assertEquals(15, placed.y(), 1e-9, "the other axis still centres");
    }

    @Test
    @DisplayName("`space` with room for one tile only places it as the position says")
    void spaceForOne() {
        var placed = PicturePainter.Placement.of(
                BackgroundSize.AUTO,
                new BackgroundRepeat(Tiling.SPACE, Tiling.SPACE),
                BackgroundPosition.CENTRE,
                AREA,
                30,
                30);

        assertFalse(placed.down());
        assertEquals(5, placed.y(), 1e-9);
    }

    @Test
    @DisplayName("`round` stretches the tile to a whole number, and keeps an auto axis's shape")
    void round() {
        var placed = place(new BackgroundRepeat(Tiling.ROUND, Tiling.NO_REPEAT), BackgroundPosition.ZERO);

        assertEquals(100.0 / 3, placed.width(), 1e-9);
        assertEquals(placed.width(), placed.stepX(), 1e-9);
        assertEquals(10 * (100.0 / 3) / 30, placed.height(), 1e-9);
    }

    @Test
    @DisplayName("spread tiles that start before the box are counted from the first that reaches it")
    void tiles() {
        var tiles = PicturePainter.Tiles.of(-50, 0, 30, 10, 35, 10, 100, 40, true, false);

        assertNotNull(tiles);
        assertEquals(1, tiles.firstColumn(), "the tile at -50 ends at -20; the one at -15 reaches in");
        assertEquals(4, tiles.columns());
    }
}
