package io.github.digitalsmile.goldberry.css;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.motion.Easing;
import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.Justify;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ComputedStyleTest {

    /// The whole pipeline: parse, cascade, substitute, compute. Every test here
    /// goes through it, because the seams between those stages are where a
    /// property gets lost.
    private static ComputedStyle compute(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var root = element("window");
        root.with(element("button"));
        var declarations = new StyleResolver(List.of(sheet)).resolve(root.descend(1));
        return ComputedStyle.of(declarations, CssLength.Context.DEFAULT);
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("a node with no rules gets the initial style, not a null")
        void initial() {
            var style = compute("input { color: red }");

            assertSame(ComputedStyle.INITIAL, ComputedStyle.of(java.util.Map.of(), CssLength.Context.DEFAULT));
            assertEquals(FlexDirection.ROW, style.direction());
            assertEquals(CssColor.TRANSPARENT, style.background());
            assertEquals(1.0, style.opacity());
        }

        @Test
        @DisplayName("the initial style is not already themed")
        void initialIsNotNord() {
            // A default that looks designed makes a stylesheet that failed to
            // load look like one that worked.
            assertEquals(CssColor.TRANSPARENT, ComputedStyle.INITIAL.background());
            assertEquals(0xFF000000, ComputedStyle.INITIAL.color());
        }
    }

    @Nested
    @DisplayName("a shorthand with a function in it")
    class Functions {

        @Test
        @DisplayName("keeps the spaces inside the function's own parentheses")
        void borderWithRgba() {
            // The bug this pins was live and silent: the splitter broke a
            // shorthand on *any* whitespace, so `rgba(255, 255, 255, 0.2)`
            // became four fragments, none of them a colour, and the whole
            // `border` was dropped with a warning nobody was reading.
            var style = compute("button { border: 1px solid rgba(255, 255, 255, 0.2) }");

            assertTrue(style.decoration().hasBorder());
        }

        @Test
        @DisplayName("and the same value written without spaces means the same thing")
        void spacingDoesNotMatter() {
            var spaced = compute("button { border: 1px solid rgba(255, 255, 255, 0.2) }");
            var tight = compute("button { border: 1px solid rgba(255,255,255,0.2) }");

            assertEquals(tight.decoration(), spaced.decoration());
        }

        @Test
        @DisplayName("a token that is what a card's edge actually resolves to")
        void theCardEdge() {
            // `--gb-border-strong`, through a custom property, which is how it
            // reaches the shorthand in the real stylesheet -- an alpha over
            // whatever is underneath is the only way to say "lighter than its
            // own surface" in a subset with no colour functions (ADR-0166).
            var style = compute("window { --edge: rgba(255, 255, 255, 0.20) }"
                    + " button { border: 1px solid var(--edge) }");

            assertTrue(style.decoration().hasBorder(),
                    "a raised thing is told apart by its edge, and it had none");
        }
    }

    @Nested
    @DisplayName("layout properties compile to Yoga")
    class Layout {

        @Test
        @DisplayName("keywords map onto Yoga's enums by name")
        void keywords() {
            var style = compute("""
                    button {
                      flex-direction: column;
                      justify-content: space-between;
                      align-items: center;
                    }
                    """);

            assertEquals(FlexDirection.COLUMN, style.direction());
            assertEquals(Justify.SPACE_BETWEEN, style.justifyContent());
            assertEquals(Align.CENTER, style.alignItems());
        }

        @Test
        @DisplayName("lengths become points, percents and auto")
        void lengths() {
            var style = compute("button { width: 120px; height: 50%; padding: 8px }");

            assertEquals(StyleLength.points(120), style.width());
            assertEquals(StyleLength.percent(50), style.height());
            assertEquals(Insets.all(StyleLength.points(8)), style.padding());
        }

        @Test
        @DisplayName("auto is a length")
        void auto() {
            assertEquals(StyleLength.AUTO, compute("button { width: auto }").width());
        }

        @Test
        @DisplayName("flex-grow is a plain number")
        void flexGrow() {
            assertEquals(2.0, compute("button { flex-grow: 2 }").flexGrow());
        }
    }

    @Nested
    @DisplayName("relative units")
    class RelativeUnits {

        @Test
        @DisplayName("em multiplies the font size in force")
        void em() {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { padding: 1.5em }");
            var root = element("window");
            root.with(element("button"));
            var declarations = new StyleResolver(List.of(sheet)).resolve(root.descend(1));

            var style = ComputedStyle.of(declarations, new CssLength.Context(20, 16));
            assertEquals(Insets.all(StyleLength.points(30)), style.padding());
        }

        @Test
        @DisplayName("rem multiplies the root font size, not the local one")
        void rem() {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { padding: 2rem }");
            var root = element("window");
            root.with(element("button"));
            var declarations = new StyleResolver(List.of(sheet)).resolve(root.descend(1));

            // Local 20, root 16 -- rem must ignore the 20.
            var style = ComputedStyle.of(declarations, new CssLength.Context(20, 16));
            assertEquals(Insets.all(StyleLength.points(32)), style.padding());
        }

        @Test
        @DisplayName("a unitless zero is a length; any other unitless number is not")
        void unitlessZero() {
            assertEquals(Insets.all(StyleLength.points(0)), compute("button { padding: 0 }").padding());
            // "padding: 8" is an author error, and guessing px would hide it.
            assertEquals(ComputedStyle.INITIAL.padding(), compute("button { padding: 8 }").padding());
        }
    }

    @Nested
    @DisplayName("paint properties")
    class Paint {

        @Test
        @DisplayName("background and color resolve to packed ARGB")
        void colours() {
            var style = compute("button { background: #2e3440; color: #eceff4 }");

            assertEquals(0xFF2E3440, style.background());
            assertEquals(0xFFECEFF4, style.color());
        }

        @Test
        @DisplayName("background-color is a spelling of background")
        void backgroundColorAlias() {
            assertEquals(0xFFFF0000, compute("button { background-color: red }").background());
        }

        @Test
        @DisplayName("opacity clamps into 0..1")
        void opacity() {
            assertEquals(0.5, compute("button { opacity: 0.5 }").opacity());
            assertEquals(1.0, compute("button { opacity: 4 }").opacity());
            assertEquals(0.0, compute("button { opacity: -1 }").opacity());
        }
    }

    @Nested
    @DisplayName("bad values")
    class BadValues {

        @Test
        @DisplayName("an unparseable value is dropped, and the rest of the node survives")
        void oneBadDeclaration() {
            // This runs per node per restyle inside the frame loop; throwing
            // would take a window down over one typo.
            var style = compute("button { background: notacolour; color: #eceff4 }");

            assertEquals(ComputedStyle.INITIAL.background(), style.background());
            assertEquals(0xFFECEFF4, style.color());
        }

        @Test
        @DisplayName("an unknown property is ignored rather than fatal")
        void unknownProperty() {
            // §8's property list is longer than this record; a stylesheet naming
            // box-shadow early must not stop a window opening.
            var style = compute("button { box-shadow: 0 1px 2px black; color: red }");
            assertEquals(0xFFFF0000, style.color());
        }

        @Test
        @DisplayName("a keyword that is not in the enum is dropped")
        void badKeyword() {
            assertEquals(ComputedStyle.INITIAL.direction(),
                    compute("button { flex-direction: sideways }").direction());
        }

        @Test
        @DisplayName("a negative flex-grow is dropped")
        void negativeFlexGrow() {
            assertEquals(ComputedStyle.INITIAL.flexGrow(),
                    compute("button { flex-grow: -1 }").flexGrow());
        }
    }

    @Nested
    @DisplayName("through the whole pipeline")
    class Pipeline {

        @Test
        @DisplayName("a themed button computes from tokens the base rule never mentions")
        void themedButton() {
            var base = Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, """
                    :root { --gb-bg: #eceff4; --gb-pad: 4px }
                    button {
                      background: var(--gb-bg);
                      padding: var(--gb-pad);
                      flex-direction: row;
                    }
                    """);
            var theme = Stylesheet.parse(CascadeLayer.THEME, """
                    :root { --gb-bg: #2e3440; --gb-pad: 8px }
                    """);
            var app = Stylesheet.parse(CascadeLayer.APPLICATION, """
                    button.primary { flex-direction: column }
                    """);

            var root = element("window");
            root.with(element("button.primary"));
            var declarations = new StyleResolver(List.of(base, theme, app)).resolve(root.descend(1));
            var style = ComputedStyle.of(declarations, CssLength.Context.DEFAULT);

            assertEquals(0xFF2E3440, style.background());
            assertEquals(Insets.all(StyleLength.points(8)), style.padding());
            assertEquals(FlexDirection.COLUMN, style.direction());
        }
    }

    @Nested
    @DisplayName("padding")
    class Padding {

        @Test
        @DisplayName("one value is every edge")
        void one() {
            assertEquals(Insets.all(StyleLength.points(12)),
                    compute("button { padding: 12px }").padding());
        }

        @Test
        @DisplayName("two values are vertical then horizontal")
        void two() {
            // The form a control is written in: `padding: 0 12px` is the button's
            // own metric, and supporting only one value would mean no control
            // could state it.
            assertEquals(new Insets(StyleLength.points(0), StyleLength.points(12),
                            StyleLength.points(0), StyleLength.points(12)),
                    compute("button { padding: 0 12px }").padding());
        }

        @Test
        @DisplayName("three values give the bottom its own, and the sides share")
        void three() {
            assertEquals(new Insets(StyleLength.points(1), StyleLength.points(2),
                            StyleLength.points(3), StyleLength.points(2)),
                    compute("button { padding: 1px 2px 3px }").padding());
        }

        @Test
        @DisplayName("four values run clockwise from the top, as CSS does")
        void four() {
            // CSS's order, not a reading order. Two orders for one concept is how
            // a padding lands on the wrong pair of edges.
            assertEquals(new Insets(StyleLength.points(1), StyleLength.points(2),
                            StyleLength.points(3), StyleLength.points(4)),
                    compute("button { padding: 1px 2px 3px 4px }").padding());
        }

        @Test
        @DisplayName("a longhand overrides one edge of the shorthand before it")
        void longhand() {
            var style = compute("button { padding: 4px; padding-left: 16px }");

            assertEquals(new Insets(StyleLength.points(4), StyleLength.points(4),
                    StyleLength.points(4), StyleLength.points(16)), style.padding());
        }

        @Test
        @DisplayName("a shorthand with one bad part is dropped whole")
        void partiallyBad() {
            // Half a shorthand is harder to see than none of it: two edges would
            // move and two would not, which reads as a layout bug.
            assertEquals(ComputedStyle.INITIAL.padding(),
                    compute("button { padding: 8px nonsense }").padding());
        }

        @Test
        @DisplayName("five values are not a shorthand CSS has")
        void tooMany() {
            assertEquals(ComputedStyle.INITIAL.padding(),
                    compute("button { padding: 1px 2px 3px 4px 5px }").padding());
        }
    }

    @Nested
    @DisplayName("typography (§1.4)")
    class Typography {

        @Test
        @DisplayName("size, family and weight resolve")
        void resolves() {
            var style = compute("""
                    button { font-family: "JetBrains Mono"; font-size: 20px; font-weight: 600 }
                    """);

            assertEquals("JetBrains Mono", style.typography().family());
            assertEquals(20, style.typography().size(), 1e-9);
            assertEquals(BundledFont.Weight.SEMI_BOLD, style.typography().weight());
        }

        @Test
        @DisplayName("a weight no face ships resolves to the nearer one it does")
        void nearestWeight() {
            // CSS's own matching, in the only form two faces need. `bold` and 900
            // both land on SemiBold, which is the honest answer -- the
            // alternative is a heading that silently renders at 400.
            assertEquals(BundledFont.Weight.SEMI_BOLD,
                    compute("button { font-weight: bold }").typography().weight());
            assertEquals(BundledFont.Weight.SEMI_BOLD,
                    compute("button { font-weight: 900 }").typography().weight());
            assertEquals(BundledFont.Weight.REGULAR,
                    compute("button { font-weight: 500 }").typography().weight());
            assertEquals(BundledFont.Weight.REGULAR,
                    compute("button { font-weight: normal }").typography().weight());
        }

        @Test
        @DisplayName("only the first family of a list is taken")
        void noFallbackChain() {
            // §6.1 is explicit that there is no fallback cascade in v1: a
            // character outside the bundled faces is .notdef on purpose.
            // Honouring the rest of the list would pretend to a mechanism that
            // does not exist.
            assertEquals("Inter",
                    compute("button { font-family: Inter, sans-serif }").typography().family());
        }

        @Test
        @DisplayName("a bare line-height is a multiple of the size")
        void lineHeightRatio() {
            // The form that survives a font-size change on a descendant: 1.4
            // gives a 20px heading a 28px line box and an 11px caption a 15px
            // one, where an inherited absolute 18px would give both the same.
            var style = compute("button { font-size: 20px; line-height: 1.4 }");

            assertEquals(28, style.typography().resolvedLineHeight(), 1e-9);
        }

        @Test
        @DisplayName("an absolute line-height is used as written")
        void lineHeightLength() {
            assertEquals(18,
                    compute("button { line-height: 18px }").typography().resolvedLineHeight(), 1e-9);
        }

        @Test
        @DisplayName("the default is §1.4's body: Inter 400 at 13/18")
        void initial() {
            // Deliberately the specified default rather than something neutral:
            // a window with no stylesheet should read as the design system.
            var body = ComputedStyle.INITIAL.typography();

            assertEquals("Inter", body.family());
            assertEquals(13, body.size(), 1e-9);
            assertEquals(18, body.resolvedLineHeight(), 1e-9);
            assertEquals(BundledFont.Weight.REGULAR, body.weight());
        }

        @Test
        @DisplayName("a size that is not a positive length is dropped")
        void badSize() {
            assertEquals(ComputedStyle.INITIAL.typography().size(),
                    compute("button { font-size: 0 }").typography().size(), 1e-9);
            assertEquals(ComputedStyle.INITIAL.typography().size(),
                    compute("button { font-size: 50% }").typography().size(), 1e-9);
        }
    }

    @Nested
    @DisplayName("transitions (§1.7)")
    class TransitionParsing {

        @Test
        @DisplayName("property, duration, easing and delay")
        void full() {
            var timing = compute("button { transition: background-color 100ms ease-exit 20ms }")
                    .transitions().get(Transitions.Animatable.BACKGROUND_COLOR);

            assertEquals(100, timing.durationMillis(), 1e-9);
            assertEquals(Easing.EASE_EXIT, timing.easing());
            assertEquals(20, timing.delayMillis(), 1e-9);
        }

        @Test
        @DisplayName("seconds and milliseconds both work; a bare number does not")
        void units() {
            assertEquals(160, compute("button { transition: opacity 0.16s }")
                    .transitions().get(Transitions.Animatable.OPACITY).durationMillis(), 1e-9);

            // `transition: color 200` almost certainly means milliseconds, and
            // guessing would make the one stylesheet that meant seconds silently
            // wrong.
            assertTrue(compute("button { transition: color 200 }").transitions().isEmpty());

            // Zero is the exception, because it has no duration to be wrong
            // about -- the same allowance a length gets.
            assertEquals(0, compute("button { transition: color 0 }")
                    .transitions().get(Transitions.Animatable.COLOR).durationMillis(), 1e-9);
        }

        @Test
        @DisplayName("a comma-separated list is several transitions")
        void list() {
            var transitions = compute("""
                    button { transition: background-color 100ms ease-enter,
                                         color 160ms linear }
                    """).transitions();

            assertEquals(2, transitions.byProperty().size());
            assertEquals(Easing.LINEAR,
                    transitions.get(Transitions.Animatable.COLOR).easing());
        }

        @Test
        @DisplayName("a layout property is refused, not ignored")
        void layoutPropertyRefused() {
            // §1.7: "layout properties never transition". Animating a width would
            // run Yoga every frame of every transition. An author who asked for
            // one is asking for something the system deliberately will not do,
            // and needs to be told rather than left with a rule that never fires.
            assertTrue(compute("button { transition: width 200ms }").transitions().isEmpty());
            assertTrue(compute("button { transition: padding 200ms }").transitions().isEmpty());
        }

        @Test
        @DisplayName("one bad entry drops the whole declaration")
        void allOrNothing() {
            // Half a list is worse than none: the author sees two of their three
            // properties moving and has nothing to say which one was refused.
            assertTrue(compute("""
                    button { transition: color 100ms, width 100ms }
                    """).transitions().isEmpty());
        }

        @Test
        @DisplayName("`none` turns off what an earlier rule declared")
        void none() {
            assertTrue(compute("""
                    button { transition: color 100ms }
                    button { transition: none }
                    """).transitions().isEmpty());
        }

        @Test
        @DisplayName("`background` is accepted as the colour, since that is all there is")
        void backgroundSynonym() {
            assertEquals(100, compute("button { transition: background 100ms }")
                    .transitions().get(Transitions.Animatable.BACKGROUND_COLOR)
                    .durationMillis(), 1e-9);
        }

        @Test
        @DisplayName("a curve CSS has and this system does not is refused")
        void unknownEasing() {
            assertTrue(compute("button { transition: color 100ms ease-in-out }")
                    .transitions().isEmpty());
        }

        @Test
        @DisplayName("the default curve is ease-enter")
        void defaultEasing() {
            assertEquals(Easing.EASE_ENTER, compute("button { transition: color 100ms }")
                    .transitions().get(Transitions.Animatable.COLOR).easing());
        }
    }

    @Nested
    @DisplayName("inheritance")
    class Inheritance {

        /// A parent and a child, each resolved against the same sheet, with the
        /// parent's computed style handed to the child — which is what
        /// `WidgetRenderer` does on the way down the element tree.
        private ComputedStyle child(String css) {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
            var root = element("panel");
            root.with(element("text"));
            var resolver = new StyleResolver(List.of(sheet));

            var parent = ComputedStyle.of(
                    resolver.resolve(root), CssLength.Context.DEFAULT);
            return ComputedStyle.of(
                    resolver.resolve(root.descend(1)), CssLength.Context.DEFAULT, parent);
        }

        @Test
        @DisplayName("`color` inherits")
        void colorInherits() {
            // The bug this closed: a checkbox sets `color` and its label is a
            // `text` child element that no rule names, so without inheritance it
            // resolved to INITIAL's black -- invisible on a dark theme.
            assertEquals(0xFFAABBCC, child("panel { color: #abc }").color());
        }

        @Test
        @DisplayName("a child's own declaration wins over what it inherits")
        void ownWins() {
            assertEquals(0xFF112233,
                    child("panel { color: #abc } text { color: #123 }").color());
        }

        @Test
        @DisplayName("the typography inherits, which is what a class on a container is for")
        void typographyInherits() {
            var style = child("panel { font-size: 20px; font-weight: 600 }");

            assertEquals(20, style.typography().size(), 1e-9);
            assertEquals(BundledFont.Weight.SEMI_BOLD, style.typography().weight());
        }

        @Test
        @DisplayName("`transition` does not inherit")
        void transitionsDoNot() {
            // CSS does not inherit it, and a panel that faded its background
            // must not make every label inside it fade too. A control declares
            // what *it* animates.
            assertTrue(child("panel { transition: color 100ms }").transitions().isEmpty());
        }

        @Test
        @DisplayName("`background` does not inherit")
        void backgroundDoesNot() {
            // The half of CSS's split that matters most here: a child inheriting
            // its parent's background would paint it a second time, and a
            // transparent child is what makes a tree of boxes cheap.
            assertEquals(ComputedStyle.INITIAL.background(),
                    child("panel { background: #abc }").background());
        }

        @Test
        @DisplayName("layout properties do not inherit either")
        void layoutDoesNot() {
            var style = child("panel { padding: 12px; height: 32px; gap: 8px }");

            assertEquals(ComputedStyle.INITIAL.padding(), style.padding());
            assertEquals(ComputedStyle.INITIAL.height(), style.height());
            assertEquals(ComputedStyle.INITIAL.gap(), style.gap());
        }

        @Test
        @DisplayName("`opacity` does not inherit, because its effect already does")
        void opacityDoesNot() {
            // The painter accumulates opacity down the box tree (ADR-0064).
            // Inheriting the value here as well would apply it once per level:
            // a label under a control at 45% would be drawn at 20%.
            assertEquals(1.0, child("panel { opacity: 0.45 }").opacity(), 1e-9);
        }

        @Test
        @DisplayName("`cursor` does not inherit here, because it inherits elsewhere")
        void cursorDoesNot() {
            // ADR-0057: the cursor rides on the painted box and hit testing reads
            // it off whichever rectangle the pointer is over. A second mechanism
            // would disagree with the first the moment a box had no element.
            assertEquals(ComputedStyle.INITIAL.cursor(),
                    child("panel { cursor: pointer }").cursor());
        }

        @Test
        @DisplayName("the decoration does not inherit")
        void decorationDoesNot() {
            var style = child("panel { border-radius: 8px; border: 1px solid #abc }");

            assertEquals(0, style.decoration().radius(), 1e-9);
            assertEquals(false, style.decoration().hasBorder());
        }

        @Test
        @DisplayName("a null parent is the root, and inherits nothing")
        void rootInheritsNothing() {
            assertSame(ComputedStyle.INITIAL,
                    ComputedStyle.of(java.util.Map.of(), CssLength.Context.DEFAULT, null));
        }
    }

    /// What a declaration that cannot be applied says, and how often.
    ///
    /// A stylesheet is static, so a value that is not one cannot become one on
    /// the next frame — but a style is resolved per element per invalidation, so
    /// before this was deduplicated a single typo reported itself for every
    /// element on every frame the screen moved. The point of a warning is that
    /// somebody reads it, and one line does not survive a thousand identical ones
    /// after it.
    @org.junit.jupiter.api.Nested
    @org.junit.jupiter.api.DisplayName("reporting a declaration that cannot be applied")
    class Dropping {

        @org.junit.jupiter.api.BeforeEach
        void forget() {
            ComputedStyle.forgetReportedDrops();
        }

        /// The value is still dropped every time — only the *report* is once.
        /// Making the drop itself conditional would be a stylesheet that behaved
        /// differently on the second frame.
        @Test
        @DisplayName("the declaration is dropped every time, however often it is reported")
        void alwaysDropped() {
            for (var attempt = 0; attempt < 3; attempt++) {
                var style = compute("button { align-items: start; gap: 4px }");
                assertEquals(ComputedStyle.INITIAL.alignItems(), style.alignItems(),
                        "a value this toolkit has not got must never be applied");
                assertEquals(io.github.digitalsmile.goldberry.natives.yoga.StyleLength.points(4),
                        style.gap(), "and the declarations around it still are");
            }
        }

        /// `start` is CSS's alias for `flex-start` and Yoga has only the second,
        /// which is the exact typo that produced the report this test exists for.
        @Test
        @DisplayName("`start` is not `flex-start`, which is the typo that started this")
        void startIsNotFlexStart() {
            assertEquals(io.github.digitalsmile.goldberry.natives.yoga.Align.FLEX_START,
                    compute("button { align-items: flex-start }").alignItems());
            assertEquals(ComputedStyle.INITIAL.alignItems(),
                    compute("button { align-items: start }").alignItems());
        }
    }
}
