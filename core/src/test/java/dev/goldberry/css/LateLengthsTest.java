package dev.goldberry.css;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.media.MediaContext;
import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.FontUnits;
import dev.goldberry.css.value.Viewport;
import dev.goldberry.layout.Length;

/// The lengths that resolve late: the viewport units, `ch` and `ex`, and the
/// math functions, as the cascade computes them.
class LateLengthsTest {

    /// `ch` is 0.6em and `ex` 0.4em in "Mono", and the fallback's half an em
    /// in any other face, so a test can see which face measured it.
    private record Measures(String family) implements FontUnits {

        @Override
        public double ch() {
            return family.equals("Mono") ? 0.6 : 0.5;
        }

        @Override
        public double ex() {
            return family.equals("Mono") ? 0.4 : 0.5;
        }

        @Override
        public FontUnits face(String name, int weight) {
            return new Measures(name);
        }
    }

    /// The root `em` is 16px, the window 1000 by 500, and fonts measured by
    /// [Measures].
    private static final CssLength.Context CONTEXT =
            new CssLength.Context(16, 16).withViewport(1000, 500).withFontUnits(new Measures("Inter"));

    /// A `button` under a `window` whose font size is 20px.
    private static ComputedStyle child(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "window { font-size: 20px } " + css);
        var root = element("window");
        root.with(element("button"));
        var resolver = new StyleResolver(List.of(sheet));
        var parent = ComputedStyle.of(resolver.resolve(root), CONTEXT, null);
        return ComputedStyle.of(resolver.resolve(root.descend(1)), CONTEXT, parent);
    }

    private static String width(String value) {
        return child("button { width: " + value + " }").width().toString();
    }

    @Nested
    @DisplayName("viewport units")
    class ViewportUnits {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "50vw,   500.0px",
            "50vh,   250.0px",
            "10vmin, 50.0px",
            "10vmax, 100.0px",
            "100dvh, 500.0px",
            "100svh, 500.0px",
            "100lvw, 1000.0px",
            "10vi,   100.0px",
        })
        @DisplayName("are a percentage of the window")
        void ofTheWindow(String value, String expected) {
            assertEquals(expected, width(value));
        }

        @Test
        @DisplayName("are not lengths before a window says how big it is")
        void unknownWindow() {
            var tokens = CssTokenizer.tokenize("50vw").stream()
                    .filter(t -> !t.is(TokenType.EOF))
                    .toList();
            assertEquals(null, CssLength.parse(tokens, CssLength.Context.DEFAULT));
            assertFalse(Viewport.UNKNOWN.isKnown());
            assertThrows(IllegalArgumentException.class, () -> new Viewport(1, 1).resolve(1, "em"));
        }
    }

    @Nested
    @DisplayName("ch and ex")
    class FontUnitsInTheFace {

        @Test
        @DisplayName("are measured in the element's own face at its own size")
        void ownFace() {
            assertEquals("12.0px", width("1ch; font-family: Mono"));
            assertEquals("8.0px", width("1ex; font-family: Mono"));
            assertEquals("10.0px", width("1ch"));
        }

        @Test
        @DisplayName("in font-size, are measured in the parent's face, as em is its size")
        void fontSizeReadsTheParent() {
            var style = child("window { font-family: Mono } button { font-family: Inter; font-size: 2ch }");
            assertEquals(24, style.typography().size(), 1e-6);
        }

        @Test
        @DisplayName("are half an em where nothing measured the face")
        void fallback() {
            var style = ComputedStyle.of(
                    Map.of(
                            "width",
                            CssTokenizer.tokenize("4ch").stream()
                                    .filter(t -> !t.is(TokenType.EOF))
                                    .toList()),
                    new CssLength.Context(16, 16));
            assertEquals(Length.points((float) (4 * 0.5 * Typography.INITIAL.size())), style.width());
        }
    }

    @Nested
    @DisplayName("math functions")
    class MathFunctions {

        @Test
        @DisplayName("fold to a length, em measured at the element's own size")
        void fold() {
            assertEquals("30.0px", width("calc(10px + 1em)"));
            assertEquals("60.0%", width("calc(50% + 10%)"));
            assertEquals("520.0px", width("clamp(100px, 50vw + 1em, 900px)"));
        }

        @Test
        @DisplayName("a length mixed with a percentage is dropped for layout")
        void mixedRefusedForLayout() {
            assertEquals(
                    child("").width(),
                    child("button { width: calc(100% - 8px) }").width());
            assertFalse(ComputedStyle.applies("margin", tokens("calc(100% - 8px) 0"), CONTEXT));
            assertFalse(ComputedStyle.applies("width", tokens("min(100px, 50%)"), CONTEXT));
        }

        @Test
        @DisplayName("is read in every slot of a shorthand")
        void shorthands() {
            assertTrue(ComputedStyle.applies("padding", tokens("clamp(4px, 1em, 12px) calc(2px * 3)"), CONTEXT));
            var style = child("button { padding: calc(1em / 2) 3px }");
            assertEquals(Length.points(10), style.padding().top());
        }

        @Test
        @DisplayName("a mixed length resolves against its basis, and the layout bridge has no spelling for one")
        void mixedLength() {
            var mixed = Length.mixed(-8, 100);
            assertEquals(92, Length.resolve(mixed, 100), 1e-6);
            assertEquals(Length.points(3), Length.mixed(3, 0));
            assertEquals(Length.percent(3), Length.mixed(0, 3));
            assertEquals("calc(100.0% + -8.0px)", mixed.toString());
        }

        private static List<Token> tokens(String text) {
            return CssTokenizer.tokenize(text).stream()
                    .filter(t -> !t.is(TokenType.EOF))
                    .toList();
        }
    }

    @Nested
    @DisplayName("a resize")
    class Resize {

        @Test
        @DisplayName("restyles a sheet that writes a viewport unit")
        void restylesViewportSheets() {
            var resolver = new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "a { width: 50vw }")))
                    .under(MediaContext.UNKNOWN.size(800, 600));
            assertNotSame(resolver, resolver.under(MediaContext.UNKNOWN.size(801, 600)));
            assertSame(resolver, resolver.under(MediaContext.UNKNOWN.size(800, 600)));
        }

        @Test
        @DisplayName("restyles a sheet whose custom property holds one")
        void customProperties() {
            var resolver = new StyleResolver(List.of(
                            Stylesheet.parse(CascadeLayer.APPLICATION, "a { --gutter: 2vh; padding: var(--gutter) }")))
                    .under(MediaContext.UNKNOWN.size(800, 600));
            assertNotSame(resolver, resolver.under(MediaContext.UNKNOWN.size(800, 601)));
        }

        @Test
        @DisplayName("leaves a sheet without viewport units alone")
        void leavesOthersAlone() {
            var resolver = new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "a { width: 50% }")))
                    .under(MediaContext.UNKNOWN.size(800, 600));
            assertSame(resolver, resolver.under(MediaContext.UNKNOWN.size(1200, 900)));
        }
    }
}
