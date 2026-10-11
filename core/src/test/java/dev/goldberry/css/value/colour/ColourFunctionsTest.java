package dev.goldberry.css.value.colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.value.CssColor;

/// CSS Color 4's functions and names, read through [CssColor#parse(String)] as
/// a stylesheet's value would be.
///
/// The reference colours are the published conversions of sRGB's primaries and
/// greys. A channel may land one step off the hex after the round trip through
/// another space, so those compare within one.
class ColourFunctionsTest {

    private static int parse(String text) {
        var colour = CssColor.parse(text);
        assertNotNull(colour, () -> text + " did not parse");
        return colour;
    }

    private static void assertClose(int expected, int actual, String what) {
        for (var shift = 0; shift <= 24; shift += 8) {
            var e = (expected >>> shift) & 0xFF;
            var a = (actual >>> shift) & 0xFF;
            assertTrue(
                    Math.abs(e - a) <= 1,
                    () -> what + ": expected " + Integer.toHexString(expected) + ", was "
                            + Integer.toHexString(actual));
        }
    }

    @Nested
    @DisplayName("each function")
    class Functions {

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "hsl(120 100% 25%)                     | ff008000",
                    "hsl(120deg 100% 25%)                  | ff008000",
                    "hsl(0.5turn 100% 50%)                 | ff00ffff",
                    "hsl(120, 100%, 25%)                   | ff008000",
                    "hsla(120, 100%, 25%, 0.5)             | 80008000",
                    "hsl(120 100 25 / 50%)                 | 80008000",
                    "hsl(none 0% 50%)                      | ff808080",
                    "hwb(0 0% 0%)                          | ffff0000",
                    "hwb(0 50% 50%)                        | ff808080",
                    "hwb(240 20% 20%)                      | ff3333cc",
                    "lab(100% 0 0)                         | ffffffff",
                    "lab(0 0 0)                            | ff000000",
                    "lab(54.2905 80.8049 69.891)           | ffff0000",
                    "lch(54.2905% 106.839 40.8526)         | ffff0000",
                    "oklab(0.62796 0.22486 0.12585)        | ffff0000",
                    "oklch(62.796% 0.25768 29.2339)        | ffff0000",
                    "oklch(1 0 0)                          | ffffffff",
                    "oklch(45.201% 0.31321 264.052 / 0.5)  | 800000ff",
                    "color(srgb 1 0 0)                     | ffff0000",
                    "color(srgb 0% 50% 100%)               | ff0080ff",
                    "color(srgb-linear 0.2140 0.2140 0.2140) | ff808080",
                    "color(display-p3 1 0 0)               | ffff0000",
                    "color(xyz-d65 0.9505 1 1.089)         | ffffffff",
                })
        @DisplayName("writes the colour CSS says it does")
        void colours(String text, String argb) {
            assertClose((int) Long.parseLong(argb, 16), parse(text), text);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "hsl(120 100%)",
                    "hsl(120, 100% 25%)",
                    "hsl(120 100% 25% 0.5)",
                    "hsl(120, 100%, 25%, / 0.5)",
                    "hsl(none, 100%, 25%)",
                    "hwb(0, 0%, 0%)",
                    "lab(50% 40 30 / )",
                    "oklch(70% 0.1 20px)",
                    "color(rec2020 1 0 0)",
                    "color(srgb 1 0)",
                    "hsl(120 100% 25%) red",
                })
        @DisplayName("refuses what is not one")
        void refuses(String text) {
            assertNull(CssColor.parse(text), text);
        }

        @Test
        @DisplayName("clamps a colour outside sRGB rather than refusing it")
        void clamps() {
            assertEquals(0xFF00FF00, parse("color(srgb -1 2 0)"));
            assertEquals(0xFFFFFFFF, parse("lab(150 0 0)"));
        }
    }

    @Nested
    @DisplayName("the named colours")
    class Names {

        @Test
        @DisplayName("are CSS Color 4's 148, in any case")
        void all() {
            assertEquals(148, NamedColours.count());
            assertEquals(0xFF663399, parse("rebeccapurple"));
            assertEquals(0xFFFFA500, parse("Orange"));
            assertEquals(parse("darkgray"), parse("darkgrey"));
            assertEquals(0xFF000000, parse("black"));
            assertNull(NamedColours.get("transparent"), "a keyword of its own");
            assertEquals(CssColor.TRANSPARENT, parse("transparent"));
        }
    }
}
