package io.github.digitalsmile.goldberry.css.value;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.parse.Token;

/// `box-shadow`: what it parses, what it refuses, and how far it reaches —
/// ADR-0310.
class ShadowTest {

    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT;

    /// The tokens of a declaration value, the way the cascade hands them over.
    ///
    /// Through the real tokenizer rather than hand-built tokens, because half of
    /// what this parser has to get right is where the whitespace is — and a
    /// hand-built list is a list somebody decided the answer for.
    private static List<Token> tokens(String value) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { box-shadow: " + value + " }");
        return sheet.rules().getFirst().declarations().getFirst().value();
    }

    private static Shadow parse(String value) {
        return Shadow.parse(tokens(value), CONTEXT);
    }

    /// The whole pipeline, so a declaration is tested through the machinery that
    /// will actually deliver it — `var()` substitution included.
    private static ComputedStyle compute(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var root = element("window");
        root.with(element("button"));
        var declarations = new StyleResolver(List.of(sheet)).resolve(root.descend(1));
        return ComputedStyle.of(declarations, CONTEXT);
    }

    @Nested
    @DisplayName("parsing")
    class Parsing {

        @Test
        @DisplayName("the three-length form, which is what every elevation token writes")
        void threeLengths() {
            var shadow = parse("0 2px 8px rgba(0, 0, 0, 0.25)");

            assertNotNull(shadow);
            assertEquals(0, shadow.offsetX());
            assertEquals(2, shadow.offsetY());
            assertEquals(8, shadow.blur());
            assertEquals(0, shadow.spread());
            assertEquals(0x40000000, shadow.argb());
        }

        @Test
        @DisplayName("two lengths are enough: a hard shadow with no fade")
        void twoLengths() {
            var shadow = parse("3px 4px black");

            assertNotNull(shadow);
            assertEquals(3, shadow.offsetX());
            assertEquals(4, shadow.offsetY());
            assertEquals(0, shadow.blur());
            assertEquals(0, shadow.spread());
        }

        @Test
        @DisplayName("the fourth length is the spread, and it may be negative")
        void spread() {
            var shadow = parse("0 4px 12px -2px black");

            assertNotNull(shadow);
            assertEquals(12, shadow.blur());
            assertEquals(-2, shadow.spread());
        }

        @Test
        @DisplayName("the colour may come first, which is how a great many sheets are written")
        void colourFirst() {
            // CSS's grammar puts the colour anywhere among the lengths, and an
            // author who writes it first should not have the declaration
            // dropped.
            assertEquals(parse("0 2px 8px #ff0000"), parse("#ff0000 0 2px 8px"));
        }

        @Test
        @DisplayName("`none` is a shadow that draws nothing, not a parse failure")
        void none() {
            var shadow = parse("none");

            assertNotNull(shadow);
            assertSame(Shadow.NONE, shadow);
            assertFalse(shadow.hasInk());
        }

        @Test
        @DisplayName("a comma list is read as its first shadow")
        void commaList() {
            // The whole list is what CSS takes and one is what this draws, so the
            // question is which wrong answer: nothing, or the first. Drawing
            // something is the more useful of the two, which is the same call
            // `border: 1px dashed red` makes.
            assertEquals(parse("0 1px 2px black"), parse("0 1px 2px black, 0 8px 24px red"));
        }

        @Test
        @DisplayName("a length inside the colour function is not a length")
        void functionsAreNotSplit() {
            // `rgba(0, 0, 0, 0.25)` is one component and not four. Splitting on
            // the spaces inside it hands `CssColor.parse` the fragment `rgba(0,`,
            // which is how `--gb-border-strong` was silently absent from every
            // card for months (ADR-0215).
            var shadow = parse("0 0 4px rgba(0, 0, 0, 0.5)");

            assertNotNull(shadow);
            assertEquals(4, shadow.blur());
            assertEquals(0x80000000, shadow.argb());
        }

        @Test
        @DisplayName("`em` resolves against the font size in force, like every other length")
        void relativeLengths() {
            var shadow = Shadow.parse(tokens("0 0.5em 1em black"), new CssLength.Context(20, 16));

            assertNotNull(shadow);
            assertEquals(10, shadow.offsetY());
            assertEquals(20, shadow.blur());
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Refusals {

        /// Every value that is not a shadow, and why each one is not.
        ///
        /// A missing colour is refused rather than defaulted because CSS's
        /// default here is `currentColor`, which §8's subset does not have:
        /// guessing black would paint a hard black halo where an author meant a
        /// tinted one, so the declaration is dropped and logged. A percentage
        /// goes for `border-radius`'s reason — it means "of this box's size", and
        /// a box has no size until Yoga has run, long after the cascade.
        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "one length,                                    4px black",
            "five lengths,                                  1px 2px 3px 4px 5px black",
            "no colour and no currentColor to fall back on, 0 2px 8px",
            "a negative blur radius,                        0 2px -8px black",
            "`inset` - a different drawing entirely,        inset 0 2px 8px black",
            "a percentage the cascade cannot resolve,       0 10% 8px black",
            "two colours,                                   0 2px 8px black red",
            "nonsense,                                      lift-off",
        })
        @DisplayName("a value that is not a shadow is refused rather than half-read")
        void refused(String why, String value) {
            assertNull(parse(value), why);
        }
    }

    @Nested
    @DisplayName("how far it reaches")
    class Reach {

        @Test
        @DisplayName("a shadow with an offset reaches further one way than the other")
        void asymmetric() {
            // `0 8px 32px`: down 8 plus half the blur is 24 below, and 16 back up
            // the other way is 8 above. A single outset for all four sides would
            // repaint a band nothing drew in on three of them.
            var shadow = new Shadow(0, 8, 32, 0, 0xFF000000);

            assertEquals(24, shadow.outsetBottom());
            assertEquals(8, shadow.outsetTop());
            assertEquals(16, shadow.outsetLeft());
            assertEquals(16, shadow.outsetRight());
        }

        @Test
        @DisplayName("spread grows all four, and a negative spread shrinks them")
        void spread() {
            assertEquals(10, new Shadow(0, 0, 8, 6, 0xFF000000).outsetTop());
            assertEquals(2, new Shadow(0, 0, 8, -2, 0xFF000000).outsetTop());
        }

        @Test
        @DisplayName("a shadow that does not reach past an edge does not claim to")
        void neverNegative() {
            // Offset 20 right with a 4px blur: the shadow starts 18px right of
            // the box's left edge and never crosses it.
            assertEquals(0, new Shadow(20, 0, 4, 0, 0xFF000000).outsetLeft());
        }

        @Test
        @DisplayName("a transparent shadow reaches nowhere, however blurred")
        void transparentReachesNowhere() {
            // What keeps the damage rectangle of an ordinary box the size of the
            // box: `Decoration.NONE` carries `Shadow.NONE` and it must cost
            // nothing.
            var invisible = new Shadow(0, 40, 100, 40, 0x00000000);

            assertEquals(0, invisible.outsetBottom());
            assertFalse(invisible.hasInk());
        }
    }

    @Nested
    @DisplayName("fading and mixing")
    class Motion {

        @Test
        @DisplayName("fade scales the alpha and leaves the geometry alone")
        void fade() {
            var faded = new Shadow(0, 2, 8, 0, 0x80000000).fade(0.5);

            assertEquals(8, faded.blur());
            assertEquals(0x40, faded.argb() >>> 24);
        }

        @Test
        @DisplayName("a full-strength fade is the shadow itself")
        void fadeIdentity() {
            var shadow = new Shadow(0, 2, 8, 0, 0x80000000);
            assertSame(shadow, shadow.fade(1));
        }

        @Test
        @DisplayName("mixing interpolates every component")
        void mix() {
            var from = new Shadow(0, 2, 8, 0, 0xFF000000);
            var to = new Shadow(0, 8, 32, 4, 0xFF000000);
            var half = from.mix(to, 0.5);

            assertEquals(5, half.offsetY());
            assertEquals(20, half.blur());
            assertEquals(2, half.spread());
        }

        @Test
        @DisplayName("a shadow arriving from nothing fades in at full size rather than inflating")
        void mixFromNone() {
            // CSS's rule, and it is what the eye expects: an absent shadow
            // interpolates as the other one at zero alpha. Ramping the geometry
            // up from zero would make a card appear to inflate.
            var to = new Shadow(0, 8, 32, 0, 0xFF000000);
            var half = Shadow.NONE.mix(to, 0.5);

            assertEquals(8, half.offsetY(), "the shape is the one arriving, all the way through");
            assertEquals(32, half.blur());
            assertEquals(0x80, half.argb() >>> 24, "only the alpha moves");
        }

        @Test
        @DisplayName("and the same on the way out")
        void mixToNone() {
            var from = new Shadow(0, 8, 32, 0, 0xFF000000);
            var half = from.mix(Shadow.NONE, 0.5);

            assertEquals(8, half.offsetY());
            assertEquals(32, half.blur());
            assertEquals(0x80, half.argb() >>> 24);
        }
    }

    @Nested
    @DisplayName("through the cascade")
    class Cascade {

        @Test
        @DisplayName("the declaration reaches the computed decoration")
        void applies() {
            var style = compute("button { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25) }");

            assertTrue(style.decoration().hasShadow());
            assertEquals(8, style.decoration().shadow().blur());
        }

        @Test
        @DisplayName("a var() is substituted before the value is read")
        void throughAVariable() {
            // The whole point of the elevation tokens: a rule names the token and
            // chooses no number at all.
            var style = compute("""
                    window { --gb-elevation-1: 0 2px 8px rgba(0, 0, 0, 0.44) }
                    button { box-shadow: var(--gb-elevation-1) }
                    """);

            assertEquals(new Shadow(0, 2, 8, 0, 0x70000000), style.decoration().shadow());
        }

        @Test
        @DisplayName("a shadow that will not parse is dropped, and the rest of the node survives")
        void badValueIsDropped() {
            var style = compute("button { box-shadow: lift-off; color: #eceff4 }");

            assertFalse(style.decoration().hasShadow());
            assertEquals(0xFFECEFF4, style.color());
        }

        @Test
        @DisplayName("a box with no shadow declared is plain")
        void plainByDefault() {
            assertSame(
                    Shadow.NONE, compute("button { color: red }").decoration().shadow());
        }
    }

    @Nested
    @DisplayName("reading one back")
    class Text {

        /// What the text is *for*: reading a `box-shadow` off a computed style in
        /// a log and recognising the declaration that produced it. So the three
        /// things worth holding are asserted as facts rather than as characters —
        /// every length carries its unit, a whole number of pixels carries no
        /// decimal point, and the spread is written only when there is one, which
        /// is the difference between four fields and five.
        ///
        /// Deliberately **not** a round trip through `parse`: the colour is
        /// printed packed, `#aarrggbb`, and CSS reads eight digits as
        /// `#rrggbbaa` — so a shadow's own text parses back as a different
        /// colour, and `0px 2px 8px #40000000` comes back as `none`.
        static Stream<Arguments> printed() {
            return Stream.of(
                    arguments("a whole number of pixels", new Shadow(0, 2, 8, 0, 0x40000000), 4),
                    arguments("a shadow with a spread", new Shadow(0, 4, 12, -2, 0xFF000000), 5));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("printed")
        @DisplayName("a shadow prints its lengths with units and its colour as hex")
        void printsItsLengthsAndItsColour(String what, Shadow shadow, int fieldCount) {
            var text = shadow.toString();
            var fields = text.split(" ");

            assertEquals(fieldCount, fields.length, () -> what + " is written in " + fieldCount + " fields: " + text);
            assertFalse(text.contains("."), () -> "a whole number of pixels needs no decimal point: " + text);
            for (var i = 0; i < fieldCount - 1; i++) {
                var length = fields[i];
                assertTrue(length.endsWith("px"), () -> "a length written as " + length + " in: " + text);
            }
            assertTrue(fields[fieldCount - 1].startsWith("#"), text);
        }

        @Test
        @DisplayName("a shadow with no ink says so")
        void none() {
            assertEquals("none", Shadow.NONE.toString());
        }
    }
}
