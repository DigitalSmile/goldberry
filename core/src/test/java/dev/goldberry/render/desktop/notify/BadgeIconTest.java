package dev.goldberry.render.desktop.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.RendererRequirement;

/// The picture a Windows taskbar button shows as its badge. Drawn on any
/// machine, because it is the toolkit's own painter that draws it; only
/// handing it to the taskbar is Windows'.
@DisplayName("a Windows badge")
class BadgeIconTest {

    @ParameterizedTest(name = "\"{0}\" shows \"{1}\"")
    @CsvSource(
            value = {
                "3, 3",
                "' 12 ', 12",
                "007, 7",
                "99, 99",
                "100, 99+",
                "123456789012345678901234567890, 99+",
                "0, ''",
                "'', ''",
                "new, new",
                "!, !",
                "hello, hel",
                "😀😀😀😀, 😀😀😀"
            },
            nullValues = "NULL")
    @DisplayName("shortens its label to what fits")
    void text(String label, String shown) {
        assertEquals(shown, BadgeIcon.text(label));
    }

    @Test
    @DisplayName("shows nothing for no label")
    void none() {
        assertEquals("", BadgeIcon.text(null));
    }

    @Test
    @DisplayName("is a red disc with the label in white, transparent at the corners")
    void picture() {
        RendererRequirement.enforce();
        var picture = BadgeIcon.render("3");

        assertEquals(BadgeIcon.SIZE, picture.width());
        assertEquals(BadgeIcon.SIZE * BadgeIcon.SIZE, picture.argb().length);
        assertEquals(0, picture.at(0, 0) >>> 24, "a corner is outside the disc");
        assertEquals(0, picture.at(BadgeIcon.SIZE - 1, BadgeIcon.SIZE - 1) >>> 24);
        assertEquals(BadgeIcon.RED, picture.at(BadgeIcon.SIZE / 2, 2), "the disc's top edge is red");
        var white = countWhite(picture);
        assertTrue(white > 10, "the digit is drawn: " + white + " white pixels");
        assertTrue(white < BadgeIcon.SIZE * BadgeIcon.SIZE / 4, "and is not a white blob: " + white);
    }

    @Test
    @DisplayName("centres the label: as much of it left of the middle as right")
    void centred() {
        RendererRequirement.enforce();
        var picture = BadgeIcon.render("88");

        var left = 0;
        var right = 0;
        var top = 0;
        var bottom = 0;
        for (var y = 0; y < picture.height(); y++) {
            for (var x = 0; x < picture.width(); x++) {
                if (isWhite(picture.at(x, y))) {
                    left += x < picture.width() / 2 ? 1 : 0;
                    right += x >= picture.width() / 2 ? 1 : 0;
                    top += y < picture.height() / 2 ? 1 : 0;
                    bottom += y >= picture.height() / 2 ? 1 : 0;
                }
            }
        }
        assertTrue(Math.abs(left - right) <= (left + right) / 5, left + " left of the middle, " + right + " right");
        assertTrue(Math.abs(top - bottom) <= (top + bottom) / 5, top + " above the middle, " + bottom + " below");
    }

    @Test
    @DisplayName("draws a longer label smaller")
    void longerIsSmaller() {
        assertTrue(BadgeIcon.fontSize("99+") < BadgeIcon.fontSize("99"));
        assertTrue(BadgeIcon.fontSize("99") < BadgeIcon.fontSize("9"));
    }

    private static int countWhite(BadgeIcon.Picture picture) {
        var count = 0;
        for (var pixel : picture.argb()) {
            count += isWhite(pixel) ? 1 : 0;
        }
        return count;
    }

    /// Nearly white and nearly opaque: the inside of a stroke, not its edge.
    private static boolean isWhite(int argb) {
        return (argb >>> 24) > 0xE0
                && ((argb >> 16) & 0xFF) > 0xE0
                && ((argb >> 8) & 0xFF) > 0xE0
                && (argb & 0xFF) > 0xE0;
    }
}
