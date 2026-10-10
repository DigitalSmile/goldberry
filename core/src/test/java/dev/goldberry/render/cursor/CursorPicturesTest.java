package dev.goldberry.render.cursor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.Cursor;
import dev.goldberry.render.window.IconImage;

/// The sizes of one cursor shape, and which of them a display scale wants.
class CursorPicturesTest {

    private static CursorPicture size(int width) {
        return new CursorPicture(IconImage.of(width, width, (x, y) -> 0xFF000000), 0, 0);
    }

    /// The Gwent clone's set: 32, 48, 64 and 96 pixels, given out of order.
    private static final CursorPictures GRAB =
            new CursorPictures(Cursor.GRAB, List.of(size(64), size(32), size(96), size(48)));

    @Test
    @DisplayName("the sizes are kept smallest first, and the smallest is the shape at 100%")
    void sorted() {
        assertEquals(
                List.of(32, 48, 64, 96),
                GRAB.sizes().stream().map(CursorPicture::width).toList());
        assertEquals(32, GRAB.base().width());
    }

    @Test
    @DisplayName("a display scale picks the size nearest the base times the scale")
    void nearest() {
        assertEquals(32, GRAB.nearest(1).width());
        assertEquals(48, GRAB.nearest(1.5).width());
        assertEquals(64, GRAB.nearest(2).width());
        assertEquals(64, GRAB.nearest(1.75).width(), "56 is as near 48 as 64, and the larger wins");
        assertEquals(96, GRAB.nearest(3).width());
        assertEquals(96, GRAB.nearest(4).width(), "past the largest, the largest");
        assertEquals(32, GRAB.nearest(0.5).width());
    }

    @Test
    @DisplayName("no picture, two of one width and a hot spot outside are refused")
    void refused() {
        assertThrows(IllegalArgumentException.class, () -> new CursorPictures(Cursor.GRAB, List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> new CursorPictures(Cursor.GRAB, List.of(size(32), size(32))));
        assertThrows(IllegalArgumentException.class, () -> new CursorPicture(IconImage.of(8, 8, (x, y) -> 0), 8, 0));
    }
}
