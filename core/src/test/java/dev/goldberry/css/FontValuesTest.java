package dev.goldberry.css;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.value.CssLength;

/// The absolute units, the font size keywords and percentages,
/// `line-height: normal`, and the `font` shorthand.
class FontValuesTest {

    /// A `button` under a `window` whose font size is 20px, with the root's
    /// size at 16px.
    private static ComputedStyle child(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "window { font-size: 20px } " + css);
        var root = element("window");
        root.with(element("button"));
        var resolver = new StyleResolver(List.of(sheet));
        var context = new CssLength.Context(16, 16);
        var parent = ComputedStyle.of(resolver.resolve(root), context, null);
        return ComputedStyle.of(resolver.resolve(root.descend(1)), context.withRootFontSize(16), parent);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "1in,  96",
        "72pt, 96",
        "6pc,  96",
        "2.54cm, 96",
        "25.4mm, 96",
        "101.6Q, 96",
        "10PX, 10",
    })
    @DisplayName("an absolute unit is CSS's fixed number of pixels")
    void absoluteUnits(String length, double pixels) {
        var width = child("button { width: " + length + " }").width();
        assertEquals("" + (float) pixels + "px", width.toString());
    }

    @Nested
    @DisplayName("font-size")
    class Sizes {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "150%,     30",
            "medium,   16",
            "xx-small, 9.6",
            "x-large,  24",
            "xxx-large, 48",
            "larger,   24",
            "smaller,  16.6667",
        })
        @DisplayName("a percentage and smaller/larger step from the parent, a keyword from the root")
        void keywords(String written, double size) {
            assertEquals(
                    size,
                    child("button { font-size: " + written + " }").typography().size(),
                    1e-3);
        }
    }

    @Test
    @DisplayName("line-height: normal is 1.2 times the size")
    void normalLineHeight() {
        var typography =
                child("button { font-size: 10px; line-height: normal }").typography();
        assertEquals(12, typography.resolvedLineHeight(), 1e-9);
    }

    @Nested
    @DisplayName("the font shorthand")
    class Shorthand {

        @Test
        @DisplayName("sets every part, and resets the ones it does not name")
        void whole() {
            var typography = child("button { font-weight: 700; font-style: italic; line-height: 40px }"
                            + " button { font: 1.5em/2 \"JetBrains Mono\", monospace }")
                    .typography();
            assertEquals("JetBrains Mono", typography.family());
            assertEquals(30, typography.size(), 1e-9, "em against the parent's 20px");
            assertEquals(60, typography.resolvedLineHeight(), 1e-9);
            assertEquals(400, typography.weight(), "reset to normal");
            assertEquals(BundledFont.Style.UPRIGHT, typography.style(), "reset to normal");
        }

        @Test
        @DisplayName("reads the style and the weight in either order")
        void order() {
            var typography = child("button { font: bold italic 12px Inter }").typography();
            assertEquals(700, typography.weight());
            assertEquals(BundledFont.Style.ITALIC, typography.style());
            assertEquals(12, typography.size(), 1e-9);
            assertEquals(14.4, typography.resolvedLineHeight(), 1e-9);
        }

        @Test
        @DisplayName("a longhand after it wins, and one before it loses")
        void cascade() {
            assertEquals(
                    600,
                    child("button { font: 12px Inter; font-weight: 600 }")
                            .typography()
                            .weight());
            assertEquals(
                    400,
                    child("button { font-weight: 600; font: 12px Inter }")
                            .typography()
                            .weight());
            assertEquals(
                    12,
                    child("button { font-size: 30px; font: 12px Inter }")
                            .typography()
                            .size(),
                    1e-9);
            assertEquals(
                    30,
                    child("button { font: 12px Inter; font-size: 30px }")
                            .typography()
                            .size(),
                    1e-9);
        }

        @Test
        @DisplayName("is dropped without a size or a family")
        void incomplete() {
            assertEquals(20, child("button { font: bold Inter }").typography().size(), 1e-9);
            assertEquals(20, child("button { font: 12px }").typography().size(), 1e-9);
        }
    }
}
