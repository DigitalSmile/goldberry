package dev.goldberry.css.value.colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.value.CssColor;

/// `color-mix()`: two colours, a space, and how much of each.
class ColourMixTest {

    private static int parse(String text) {
        var colour = CssColor.parse(text);
        assertNotNull(colour, text);
        return colour;
    }

    private static void assertClose(int expected, int actual) {
        for (var shift = 0; shift <= 24; shift += 8) {
            var a = (expected >> shift) & 0xFF;
            var b = (actual >> shift) & 0xFF;
            assertTrue(
                    Math.abs(a - b) <= 1,
                    () -> "expected " + Integer.toHexString(expected) + " but was " + Integer.toHexString(actual));
        }
    }

    @Test
    @DisplayName("in sRGB, half and half is the average")
    void srgb() {
        assertEquals(0xFF800080, parse("color-mix(in srgb, red, blue)"));
    }

    @Test
    @DisplayName("a percentage on one colour leaves the rest to the other, written either side of it")
    void percentages() {
        var quarter = ColourMixing.mix(0xFFFF0000, 0xFF0000FF, 0.75, ColourSpace.SRGB, ColourMixing.HueMethod.SHORTER);
        assertEquals(quarter, parse("color-mix(in srgb, red 25%, blue)"));
        assertEquals(quarter, parse("color-mix(in srgb, 25% red, blue)"));
        assertEquals(quarter, parse("color-mix(in srgb, red, blue 75%)"));
    }

    @Test
    @DisplayName("two that add up to less than 100% leave the mix that much transparent")
    void underHundred() {
        var mixed = parse("color-mix(in srgb, red 20%, blue 30%)");
        assertEquals(0x80, mixed >>> 24);
        var opaque = ColourMixing.mix(0xFFFF0000, 0xFF0000FF, 0.6, ColourSpace.SRGB, ColourMixing.HueMethod.SHORTER);
        assertClose(0x80000000 | (opaque & 0xFFFFFF), mixed);
    }

    @Test
    @DisplayName("with no space it mixes in OKLab, and a polar space takes a hue method")
    void spaces() {
        assertEquals(
                ColourMixing.mix(0xFFFF0000, 0xFF0000FF, 0.5, ColourSpace.OKLAB, ColourMixing.HueMethod.SHORTER),
                parse("color-mix(red, blue)"));
        assertEquals(
                ColourMixing.mix(0xFFFF0000, 0xFF0000FF, 0.5, ColourSpace.HSL, ColourMixing.HueMethod.LONGER),
                parse("color-mix(in hsl longer hue, red, blue)"));
    }

    @Test
    @DisplayName("a colour inside may itself be a function, or a mix")
    void nested() {
        assertEquals(
                parse("color-mix(in srgb, red, blue)"),
                parse("color-mix(in srgb, rgb(255 0 0), color-mix(in srgb, blue, blue))"));
    }

    @Test
    @DisplayName("darkening a token by a share of black")
    void shade() {
        var shade = parse("color-mix(in oklch, #3b82f6, black 20%)");
        assertTrue((shade & 0xFF) < 0xF6 && (shade & 0xFF) > 0x80, Integer.toHexString(shade));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "color-mix(in srgb, red)",
                "color-mix(in nowhere, red, blue)",
                "color-mix(in srgb longer hue, red, blue)",
                "color-mix(in srgb, red 0%, blue 0%)",
                "color-mix(in srgb, red 150%, blue)",
                "color-mix(in srgb, red -10%, blue)",
                "color-mix(in srgb, nonsense, blue)",
                "color-mix(srgb, red, blue)"
            })
    @DisplayName("refuses what is not a mix")
    void refused(String text) {
        assertNull(CssColor.parse(text), text);
    }
}
