package dev.goldberry.example.book.pictures;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.image.Image;

@DisplayName("a crop")
class CropTest {

    private static final int PAGE = 0xFFECEFF4;
    private static final int INK = 0xFF2E3440;

    /// A page with one inked box on it.
    private static Image page(int width, int height, int left, int top, int boxWidth, int boxHeight) {
        var argb = new int[width * height];
        Arrays.fill(argb, PAGE);
        for (var y = top; y < top + boxHeight; y++) {
            for (var x = left; x < left + boxWidth; x++) {
                argb[y * width + x] = INK;
            }
        }
        return Image.ofArgb(width, height, argb);
    }

    @Test
    @DisplayName("is the box around the ink, grown by the margin")
    void aroundTheInk() {
        var crop = Crop.around(page(100, 80, 30, 20, 10, 5), PAGE, 4).orElseThrow();
        assertEquals(new Crop(26, 16, 18, 13), crop);
    }

    @Test
    @DisplayName("stops at the edge of the image when the margin would leave it")
    void clampedToTheImage() {
        var crop = Crop.around(page(40, 30, 0, 0, 40, 30), PAGE, 16).orElseThrow();
        assertEquals(new Crop(0, 0, 40, 30), crop);
    }

    @Test
    @DisplayName("is empty when nothing was drawn")
    void nothingDrawn() {
        assertTrue(Crop.around(page(20, 20, 0, 0, 0, 0), PAGE, 4).isEmpty());
    }

    @Test
    @DisplayName("cuts the image down to the box, pixel for pixel")
    void applies() {
        var image = page(100, 80, 30, 20, 10, 5);
        var cut = new Crop(28, 18, 14, 9).apply(image);
        assertAll(
                () -> assertEquals(14, cut.width()),
                () -> assertEquals(9, cut.height()),
                () -> assertEquals(PAGE, cut.argb(0, 0)),
                () -> assertEquals(INK, cut.argb(2, 2)),
                () -> assertEquals(INK, cut.argb(11, 6)),
                () -> assertEquals(PAGE, cut.argb(13, 8)));
    }

    @Test
    @DisplayName("refuses a size with nothing in it")
    void refusesAnEmptyBox() {
        assertThrows(IllegalArgumentException.class, () -> new Crop(0, 0, 0, 10));
    }
}
