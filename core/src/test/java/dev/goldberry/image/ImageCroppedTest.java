package dev.goldberry.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;

/// `Image.cropped(...)`: a rectangle of a picture, copied out as a picture of
/// its own.
class ImageCroppedTest {

    /// A `width` × `height` image whose every pixel says where it is: red is the
    /// column and green the row.
    private static Image numbered(int width, int height) {
        var argb = new int[width * height];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                argb[y * width + x] = 0xFF000000 | x << 16 | y << 8;
            }
        }
        return Image.ofArgb(width, height, argb);
    }

    @Test
    @DisplayName("the copy is the rectangle's pixels, in their places")
    void copiesTheRectangle() {
        var sheet = numbered(10, 8);

        var sprite = sheet.cropped(PhysicalRect.of(3, 2, 4, 5));

        assertEquals(new PhysicalSize(4, 5), sprite.size());
        for (var y = 0; y < 5; y++) {
            for (var x = 0; x < 4; x++) {
                assertEquals(sheet.argb(3 + x, 2 + y), sprite.argb(x, y), "(" + x + ", " + y + ")");
            }
        }
    }

    @Test
    @DisplayName("a rectangle on the far edge reaches the last row and column")
    void farEdge() {
        var sheet = numbered(10, 8);

        var corner = sheet.cropped(PhysicalRect.of(7, 5, 3, 3));

        assertEquals(sheet.argb(9, 7), corner.argb(2, 2));
    }

    @Test
    @DisplayName("the whole picture is the picture itself")
    void wholeIsSame() {
        var sheet = numbered(6, 4);

        assertSame(sheet, sheet.cropped(sheet.bounds()));
    }

    @ParameterizedTest(name = "{0},{1} {2}x{3}")
    @CsvSource({"-1, 0, 2, 2", "0, -1, 2, 2", "9, 0, 2, 2", "0, 7, 2, 2", "0, 0, 11, 1", "0, 0, 1, 9"})
    @DisplayName("a rectangle that runs off the picture is refused, not cut down")
    void outside(int x, int y, int width, int height) {
        var sheet = numbered(10, 8);

        assertThrows(IllegalArgumentException.class, () -> sheet.cropped(new PhysicalRect(x, y, width, height)));
    }

    @Test
    @DisplayName("an empty rectangle is refused")
    void empty() {
        var sheet = numbered(10, 8);

        assertThrows(IllegalArgumentException.class, () -> sheet.cropped(PhysicalRect.of(2, 2, 0, 3)));
        assertThrows(IllegalArgumentException.class, () -> sheet.cropped(PhysicalRect.of(2, 2, 3, 0)));
    }
}
