package dev.goldberry.input.cursor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.image.Image;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.cursor.CursorPicture;

/// An application's cursor pictures, checked and gathered by shape before a
/// backend sees them.
class CursorImageTest {

    private static Image square(int size, int argb) {
        var pixels = new int[size * size];
        Arrays.fill(pixels, argb);
        return Image.ofArgb(size, size, pixels);
    }

    @Test
    @DisplayName("every size of a shape is gathered together, smallest first, shapes in the order named")
    void byShape() {
        var pictures = CursorImage.byShape(List.of(
                new CursorImage(Cursor.GRAB, square(64, 0xFF000000), 24, 8),
                new CursorImage(Cursor.CROSSHAIR, square(32, 0xFF000000), 16, 16),
                new CursorImage(Cursor.GRAB, square(32, 0xFF000000), 12, 4)));

        assertEquals(2, pictures.size());
        var grab = pictures.getFirst();
        assertEquals(Cursor.GRAB, grab.shape());
        assertEquals(
                List.of(32, 64), grab.sizes().stream().map(CursorPicture::width).toList());
        assertEquals(12, grab.base().hotX(), "each size keeps its own hot spot");
        assertEquals(24, grab.sizes().getLast().hotX());
        assertEquals(Cursor.CROSSHAIR, pictures.getLast().shape());
    }

    @Test
    @DisplayName("the pixels leave in straight alpha, as a window icon's do")
    void straightAlpha() {
        var pictures = CursorImage.byShape(List.of(new CursorImage(Cursor.POINTER, square(4, 0x80FF0000), 0, 0)));

        var pixel = pictures.getFirst().base().image().argb(0, 0);
        assertEquals(0x80, pixel >>> 24);
        assertEquals(0xFF, pixel >> 16 & 0xFF, "full red, not half of it: " + Integer.toHexString(pixel));
    }

    @Test
    @DisplayName("a hot spot outside the picture, and two pictures of one width, are refused")
    void refused() {
        var picture = square(32, 0xFF000000);
        assertThrows(IllegalArgumentException.class, () -> new CursorImage(Cursor.GRAB, picture, 32, 0));
        assertThrows(IllegalArgumentException.class, () -> new CursorImage(Cursor.GRAB, picture, 0, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> CursorImage.byShape(List.of(
                        new CursorImage(Cursor.GRAB, picture, 1, 1), new CursorImage(Cursor.GRAB, picture, 2, 2))));
    }
}
