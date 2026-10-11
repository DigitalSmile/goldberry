package dev.goldberry.css.value.colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.value.colour.ColourMixing.HueMethod;

/// Two colours mixed as CSS Color 4 interpolates them.
class ColourMixingTest {

    private static final int RED = 0xFFFF0000;

    private static final int BLUE = 0xFF0000FF;

    private static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    @Test
    @DisplayName("the ends are the two colours")
    void ends() {
        for (var space : ColourSpace.values()) {
            var from = ColourMixing.mix(RED, BLUE, 0, space, HueMethod.SHORTER);
            assertTrue(red(from) >= 0xFE && blue(from) <= 1, space::name);
        }
        assertEquals(RED, ColourMixing.mix(RED, BLUE, 0, ColourSpace.OKLCH, HueMethod.SHORTER));
        assertEquals(BLUE, ColourMixing.mix(RED, BLUE, 1, ColourSpace.OKLCH, HueMethod.SHORTER));
    }

    @Test
    @DisplayName("in sRGB, half way is the channels' average")
    void srgb() {
        var middle = ColourMixing.mix(RED, BLUE, 0.5, ColourSpace.SRGB, HueMethod.SHORTER);
        assertEquals(0xFF800080, middle);
    }

    @Test
    @DisplayName("in OKLab, half way from red to blue is lighter than sRGB's muddy purple")
    void oklab() {
        var srgb = ColourMixing.mix(RED, BLUE, 0.5, ColourSpace.SRGB, HueMethod.SHORTER);
        var oklab = ColourMixing.mix(RED, BLUE, 0.5, ColourSpace.OKLAB, HueMethod.SHORTER);
        assertTrue(red(oklab) + green(oklab) + blue(oklab) > red(srgb) + green(srgb) + blue(srgb));
    }

    @Test
    @DisplayName("a transparent end fades the other's colour rather than passing through its own")
    void premultiplied() {
        var middle = ColourMixing.mix(RED, 0x000000FF, 0.5, ColourSpace.SRGB, HueMethod.SHORTER);
        assertEquals(0x80, middle >>> 24);
        assertEquals(0xFF, red(middle), "all red, half there");
        assertEquals(0, blue(middle));
    }

    @Test
    @DisplayName("a grey's hue is powerless: white to blue in OKLCh stays blue")
    void powerless() {
        var middle = ColourMixing.mix(0xFFFFFFFF, BLUE, 0.5, ColourSpace.OKLCH, HueMethod.SHORTER);
        assertTrue(blue(middle) > red(middle) && blue(middle) > green(middle), Integer.toHexString(middle));
        assertTrue(red(middle) < 0xC0, "no pink on the way: " + Integer.toHexString(middle));
    }

    @Test
    @DisplayName("the long way round from red to blue passes through green")
    void longer() {
        var shorter = ColourMixing.mix(RED, BLUE, 0.5, ColourSpace.HSL, HueMethod.SHORTER);
        var longer = ColourMixing.mix(RED, BLUE, 0.5, ColourSpace.HSL, HueMethod.LONGER);
        assertEquals(0xFFFF00FF, shorter, "magenta, the short way");
        assertEquals(0xFF00FF00, longer, "green, the long way");
        assertNotEquals(shorter, longer);
    }

    @Nested
    @DisplayName("a hue's difference")
    class Turned {

        @ParameterizedTest
        @CsvSource({
            "SHORTER, 270, -90",
            "SHORTER, -270, 90",
            "SHORTER, 90, 90",
            "LONGER, 90, -270",
            "LONGER, -90, 270",
            "LONGER, 270, 270",
            "INCREASING, -90, 270",
            "INCREASING, 90, 90",
            "DECREASING, 90, -270",
            "DECREASING, -90, -90"
        })
        @DisplayName("is taken the way its method says")
        void turned(HueMethod method, double delta, double expected) {
            assertEquals(expected, ColourMixing.turned(delta, method), 1e-9);
        }

        @Test
        @DisplayName("is named by CSS's four keywords")
        void names() {
            assertEquals(HueMethod.LONGER, HueMethod.parse("Longer"));
            assertNull(HueMethod.parse("sideways"));
        }
    }
}
