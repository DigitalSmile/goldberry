package dev.goldberry.css.value.colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// Each colour space's way in and way out: a colour survives the round trip,
/// and the coordinates are the ones CSS Color 4 gives.
class ColourSpaceTest {

    private static final int[] COLOURS = {
        0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF3B82F6, 0xFF808080, 0xFFFFFFFF, 0xFF000000, 0xFFF59E0B
    };

    private static void assertClose(int expected, int actual) {
        for (var shift = 0; shift <= 24; shift += 8) {
            var a = (expected >> shift) & 0xFF;
            var b = (actual >> shift) & 0xFF;
            assertTrue(
                    Math.abs(a - b) <= 1,
                    () -> Integer.toHexString(expected) + " came back as " + Integer.toHexString(actual));
        }
    }

    @ParameterizedTest
    @EnumSource(ColourSpace.class)
    @DisplayName("an sRGB colour goes in and comes back out the same")
    void roundTrip(ColourSpace space) {
        for (var colour : COLOURS) {
            assertClose(colour, space.argb(space.coordinates(colour), 1));
        }
    }

    @Test
    @DisplayName("OKLCh gives the coordinates CSS Color 4 does")
    void oklch() {
        var red = ColourSpace.OKLCH.coordinates(0xFFFF0000);
        assertEquals(0.628, red[0], 1e-3);
        assertEquals(0.2577, red[1], 1e-3);
        assertEquals(29.23, red[2], 0.05);
    }

    @Test
    @DisplayName("Lab is D50, white at a lightness of 100 and no colour")
    void lab() {
        var white = ColourSpace.LAB.coordinates(0xFFFFFFFF);
        assertEquals(100, white[0], 0.01);
        assertEquals(0, white[1], 0.01);
        assertEquals(0, white[2], 0.01);
    }

    @Test
    @DisplayName("the names are CSS's, `xyz` being D65, and anything else is no space")
    void names() {
        assertEquals(ColourSpace.OKLCH, ColourSpace.parse("OKLCH"));
        assertEquals(ColourSpace.XYZ_D65, ColourSpace.parse("xyz"));
        assertEquals(ColourSpace.SRGB_LINEAR, ColourSpace.parse("srgb-linear"));
        assertNull(ColourSpace.parse("cmyk"));
    }

    @Test
    @DisplayName("a polar space names its hue and knows a grey has none")
    void hue() {
        assertEquals(2, ColourSpace.OKLCH.hue());
        assertEquals(0, ColourSpace.HSL.hue());
        assertEquals(-1, ColourSpace.OKLAB.hue());
        assertTrue(ColourSpace.OKLCH.isAchromatic(ColourSpace.OKLCH.coordinates(0xFF808080)));
        assertFalse(ColourSpace.OKLCH.isAchromatic(ColourSpace.OKLCH.coordinates(0xFF0000FF)));
        assertTrue(ColourSpace.HWB.isAchromatic(ColourSpace.HWB.coordinates(0xFFFFFFFF)));
    }
}
