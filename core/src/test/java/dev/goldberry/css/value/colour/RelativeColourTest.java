package dev.goldberry.css.value.colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.value.CssColor;

/// The relative colour syntax: a colour `from` another, its channels named.
class RelativeColourTest {

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

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "rgb(from #3b82f6 r g b)",
                "hsl(from #3b82f6 h s l)",
                "hwb(from #3b82f6 h w b)",
                "lab(from #3b82f6 l a b)",
                "lch(from #3b82f6 l c h)",
                "oklab(from #3b82f6 l a b)",
                "oklch(from #3b82f6 l c h)",
                "color(from #3b82f6 srgb r g b)",
                "color(from #3b82f6 display-p3 r g b)",
                "color(from #3b82f6 xyz x y z)",
                "rgb(from #3b82f6 r g b / alpha)"
            })
    @DisplayName("each function's own channels give the colour back")
    void identity(String text) {
        assertClose(0xFF3B82F6, parse(text));
    }

    @Test
    @DisplayName("a channel can be replaced, and the alpha set")
    void replaced() {
        assertEquals(0xFFFF8000, parse("rgb(from red r 128 b)"));
        assertClose(0x993B82F6, parse("rgb(from #3b82f6 r g b / 60%)"));
        assertClose(parse("hsl(217 91% 90%)"), parse("hsl(from #3b82f6 h s 90)"));
    }

    @Test
    @DisplayName("calc() does arithmetic on the names")
    void arithmetic() {
        var lighter = parse("oklch(from #3b82f6 calc(l + 0.1) c h)");
        var base = ColourSpace.OKLCH.coordinates(0xFF3B82F6);
        assertClose(ColourSpace.OKLCH.argb(new double[] {base[0] + 0.1, base[1], base[2]}, 1), lighter);
        assertEquals(0xFF800000, parse("rgb(from red calc(r / 2) g b)"), "127.5 rounds up, as CSS rounds it");
        assertClose(parse("hsl(37 100% 50%)"), parse("hsl(from orange calc(h - 2) s l)"));
    }

    @Test
    @DisplayName("the origin may itself be a function, or a mix")
    void nested() {
        assertEquals(parse("color-mix(in srgb, red, blue)"), parse("rgb(from color-mix(in srgb, red, blue) r g b)"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "rgb(from nonsense r g b)",
                "rgb(from red x y z)",
                "color(from red lab l a b)",
                "rgb(from red calc(r + 1px) g b)",
                "rgb(from)"
            })
    @DisplayName("refuses what is not a colour")
    void refused(String text) {
        assertNull(CssColor.parse(text), text);
    }
}
