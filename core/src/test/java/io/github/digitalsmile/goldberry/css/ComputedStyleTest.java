package io.github.digitalsmile.goldberry.css;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.cascade.Transitions;
import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.layout.Align;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Justify;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.motion.Easing;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.text.flow.TextDecoration;
import io.github.digitalsmile.goldberry.text.flow.TextFlow;
import io.github.digitalsmile.goldberry.text.flow.TextOverflow;
import io.github.digitalsmile.goldberry.text.flow.WhiteSpace;

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

    /// The same pipeline, with the **parent actually resolved and handed down**
    /// — which is what `WidgetRenderer` does and what an inherited font size
    /// needs ([ADR-0242]). `compute` above passes no parent, so every element it
    /// builds is a root.
    private static ComputedStyle computeChild(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var root = element("window");
        root.with(element("button"));
        var resolver = new StyleResolver(List.of(sheet));
        var parent = ComputedStyle.of(resolver.resolve(root), CssLength.Context.DEFAULT, null);
        return ComputedStyle.of(resolver.resolve(root.descend(1)), CssLength.Context.DEFAULT, parent);
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("a node with no rules gets the initial style, not a null")
        void initial() {
            var style = compute("input { color: red }");

            assertSame(ComputedStyle.INITIAL, ComputedStyle.of(java.util.Map.of(), CssLength.Context.DEFAULT));
            // And a null parent is the root, which inherits nothing.
            assertSame(ComputedStyle.INITIAL, ComputedStyle.of(java.util.Map.of(), CssLength.Context.DEFAULT, null));
            assertEquals(FlexDirection.ROW, style.direction());
            assertEquals(CssColor.TRANSPARENT, style.background());
            assertEquals(1.0, style.opacity());

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
            var style = compute(
                    "window { --edge: rgba(255, 255, 255, 0.20) }" + " button { border: 1px solid var(--edge) }");

            assertTrue(style.decoration().hasBorder(), "a raised thing is told apart by its edge, and it had none");
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

        /// [ADR-0247]: `align-items: start` is valid CSS — Box Alignment Level 3
        /// — and Yoga has only `flex-start`, so the toolkit was dropping a
        /// declaration the specification allows. This is the typo that filled the
        /// Panels screen's console and needed deduplicating before it was fixed.
        @Test
        @DisplayName("`start` and `end` are CSS's spellings of the flex pair, and are taken")
        void cssAlignmentAliases() {
            assertEquals(
                    Align.FLEX_START, compute("button { align-items: start }").alignItems());
            assertEquals(Align.FLEX_END, compute("button { align-items: end }").alignItems());
            assertEquals(
                    Justify.FLEX_START,
                    compute("button { justify-content: start }").justifyContent());
            assertEquals(
                    Justify.FLEX_END, compute("button { justify-content: end }").justifyContent());
            assertEquals(Align.FLEX_END, compute("button { align-self: end }").alignSelf());
        }

        /// The flex spellings still work, which is the half an alias table can
        /// break: a mapping applied before the enum's own lookup would shadow it.
        @Test
        @DisplayName("and the flex- spellings still mean what they always did")
        void flexSpellingsSurvive() {
            assertEquals(
                    Align.FLEX_START,
                    compute("button { align-items: flex-start }").alignItems());
            assertEquals(
                    Justify.SPACE_BETWEEN,
                    compute("button { justify-content: space-between }").justifyContent());
        }

        /// `left` and `right` are deliberately not aliases. They are
        /// `justify-content` only, they are **not** the same as `start`/`end`
        /// under RTL, and §2.4's bidi support means the toolkit cannot promise
        /// they would stay equivalent — so they are dropped like any other
        /// keyword it has not got.
        @Test
        @DisplayName("but `left` and `right` are not, because they are not the same thing under RTL")
        void directionalKeywordsAreNotAliased() {
            assertEquals(
                    ComputedStyle.INITIAL.justifyContent(),
                    compute("button { justify-content: left }").justifyContent());
        }

        @Test
        @DisplayName("`align-content` takes the three keywords nothing else could use")
        void alignContent() {
            // `SPACE_BETWEEN`, `SPACE_AROUND` and `SPACE_EVENLY` were on [Align]
            // from the start and no property accepted them, because the one they
            // belong to was not resolved (ADR-0374).
            assertEquals(
                    Align.SPACE_BETWEEN,
                    compute("button { align-content: space-between }").alignContent());
            assertEquals(
                    Align.SPACE_EVENLY,
                    compute("button { align-content: space-evenly }").alignContent());
            assertEquals(
                    Align.FLEX_START,
                    compute("button { align-content: flex-start }").alignContent());
        }

        @Test
        @DisplayName("and stretches until a rule says otherwise")
        void alignContentDefaultsToStretch() {
            assertEquals(Align.STRETCH, ComputedStyle.INITIAL.alignContent());
            assertEquals(
                    ComputedStyle.INITIAL.alignContent(),
                    compute("button { align-content: sideways }").alignContent(),
                    "a keyword CSS has not got is dropped like any other");
        }

        @Test
        @DisplayName("`flex-basis` takes a length, a percentage and `auto`")
        void flexBasis() {
            assertEquals(Length.points(0), compute("button { flex-basis: 0 }").flexBasis());
            assertEquals(
                    Length.points(120), compute("button { flex-basis: 120px }").flexBasis());
            assertEquals(
                    Length.percent(50), compute("button { flex-basis: 50% }").flexBasis());
            // The keyword that is a value rather than a missing one: it undoes a
            // more general rule, exactly as `align-self: auto` does (ADR-0373).
            assertEquals(Length.AUTO, compute("button { flex-basis: auto }").flexBasis());
        }

        @Test
        @DisplayName("and is `auto` for a box with no rule")
        void flexBasisDefaultsToAuto() {
            assertEquals(Length.AUTO, ComputedStyle.INITIAL.flexBasis());
            assertEquals(
                    ComputedStyle.INITIAL.flexBasis(),
                    compute("button { flex-basis: sideways }").flexBasis());
        }

        @Test
        @DisplayName("`flex-wrap` takes CSS's three spellings, `nowrap` included")
        void flexWrap() {
            assertEquals(
                    io.github.digitalsmile.goldberry.layout.Wrap.WRAP,
                    compute("button { flex-wrap: wrap }").wrap());
            assertEquals(
                    io.github.digitalsmile.goldberry.layout.Wrap.WRAP_REVERSE,
                    compute("button { flex-wrap: wrap-reverse }").wrap());
            // The one that needs its own line of code: CSS spells the default as
            // one word and `YGWrap` spells it as two, so the generic keyword
            // parser turns `nowrap` into `NOWRAP` and finds nothing.
            assertEquals(
                    io.github.digitalsmile.goldberry.layout.Wrap.NO_WRAP,
                    compute("button { flex-wrap: nowrap }").wrap());
        }

        @Test
        @DisplayName("one line is what a box wraps as until a rule says otherwise")
        void flexWrapDefaultsToOneLine() {
            assertEquals(io.github.digitalsmile.goldberry.layout.Wrap.NO_WRAP, ComputedStyle.INITIAL.wrap());
            assertEquals(
                    ComputedStyle.INITIAL.wrap(),
                    compute("button { flex-wrap: sideways }").wrap(),
                    "a keyword CSS has not got is dropped like any other");
        }

        /// [ADR-0244]: §8 has listed `align-items/self/content` from the
        /// beginning and only the first was built.
        @Test
        @DisplayName("align-self is a keyword like align-items, and independent of it")
        void alignSelf() {
            var style = compute("button { align-items: center; align-self: flex-end }");

            assertEquals(Align.CENTER, style.alignItems());
            assertEquals(Align.FLEX_END, style.alignSelf(), "align-self must not follow align-items");
        }

        /// `auto` is the one value that means something different on a child than
        /// it would on a parent — "defer to my container" — so it is a real
        /// declaration rather than a missing one, and undoes a more general rule.
        @Test
        @DisplayName("and auto is its default and a value it can be set back to")
        void alignSelfAuto() {
            assertEquals(Align.AUTO, ComputedStyle.INITIAL.alignSelf());
            assertEquals(Align.AUTO, compute("button { align-self: auto }").alignSelf());
            assertEquals(
                    Align.AUTO,
                    compute("button { align-self: sideways }").alignSelf(),
                    "a keyword CSS has not got is dropped like any other");
        }

        @Test
        @DisplayName("lengths become points, percents and auto")
        void lengths() {
            var style = compute("button { width: 120px; height: 50%; padding: 8px }");

            assertEquals(Length.points(120), style.width());
            assertEquals(Length.percent(50), style.height());
            assertEquals(Insets.all(Length.points(8)), style.padding());
        }

        @Test
        @DisplayName("auto is a length")
        void auto() {
            assertEquals(Length.AUTO, compute("button { width: auto }").width());
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

        /// [ADR-0242]: `em` is the element's **own** computed font size, which
        /// this used to assert was whatever number the caller put in the
        /// [CssLength.Context] — 20 here, on an element whose computed size was
        /// `Typography.INITIAL`'s 13.
        @Test
        @DisplayName("em multiplies the element's own font size")
        void em() {
            var style = compute("button { font-size: 20px; padding: 1.5em }");

            assertEquals(Insets.all(Length.points(30)), style.padding());
        }

        /// The half that needs no declaration: an element that says nothing about
        /// its size still has one, and `em` is against that.
        @Test
        @DisplayName("and against the size it starts at when it declares none")
        void emWithoutADeclaration() {
            // `Typography.INITIAL` is 13, so 1.5em is 19.5 -- not the 24 the old
            // code produced from `Context.DEFAULT`'s unrelated 16.
            assertEquals(
                    Insets.all(Length.points(19.5f)),
                    compute("button { padding: 1.5em }").padding());
        }

        /// CSS's one exception, and the reason the resolution is two passes: on
        /// `font-size` itself an `em` is the **parent's** size, because the value
        /// being computed cannot be its own input.
        @Test
        @DisplayName("but on font-size itself it is the parent's size")
        void emOnFontSizeIsTheParents() {
            var child = computeChild("window { font-size: 20px } button { font-size: 1.5em; padding: 1em }");

            assertEquals(30.0, child.typography().size(), 1e-9, "font-size resolved against something other than 20");
            // And `padding` is then against the 30 this element just became, not
            // against the 20 it inherited -- the two passes, visible in one style.
            assertEquals(Insets.all(Length.points(30)), child.padding());
        }

        /// Inheritance carries the size, so a child that declares nothing
        /// resolves `em` against what its parent computed.
        @Test
        @DisplayName("an inherited size is what a silent child resolves against")
        void emAgainstAnInheritedSize() {
            var child = computeChild("window { font-size: 20px } button { padding: 2em }");

            assertEquals(Insets.all(Length.points(40)), child.padding());
        }

        @Test
        @DisplayName("rem multiplies the root font size, not the local one")
        void rem() {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { padding: 2rem }");
            var root = element("window");
            root.with(element("button"));
            var declarations = new StyleResolver(List.of(sheet)).resolve(root.descend(1));

            // Root 16, and the element's own size is `Typography.INITIAL`'s 13
            // -- `rem` must ignore the local one either way ([ADR-0242]).
            var style = ComputedStyle.of(declarations, new CssLength.Context(20, 16));
            assertEquals(Insets.all(Length.points(32)), style.padding());
        }

        @Test
        @DisplayName("a unitless zero is a length; any other unitless number is not")
        void unitlessZero() {
            assertEquals(
                    Insets.all(Length.points(0)),
                    compute("button { padding: 0 }").padding());
            // "padding: 8" is an author error, and guessing px would hide it.
            assertEquals(
                    ComputedStyle.INITIAL.padding(),
                    compute("button { padding: 8 }").padding());
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
        @DisplayName("`background: none` turns a fill off, which is what the shorthand means")
        void backgroundNone() {
            // `select text-input` writes it, beside the `border: none` that says
            // the same thing about the edge: an editor inside a control is that
            // control's interior and has no fill of its own (ADR-0183). It was
            // dropped with a warning until ADR-0216, so the field kept the well
            // colour `text-input` gives it.
            assertEquals(
                    CssColor.TRANSPARENT,
                    compute("button { background: #2e3440; background: none }").background());
        }

        @Test
        @DisplayName("`background-color: none` is not, because `none` is not a colour")
        void backgroundColorNone() {
            // CSS's own division: `none` in the shorthand means "no layer", and
            // the longhand takes a colour or nothing. `transparent` is how the
            // longhand says it.
            assertEquals(
                    0xFF2E3440,
                    compute("button { background: #2e3440; background-color: none }")
                            .background());
            assertEquals(
                    CssColor.TRANSPARENT,
                    compute("button { background-color: transparent }").background());
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
            // a property the engine has not grown yet must not stop a window
            // opening. This was `box-shadow` for two hundred ADRs and is
            // `backdrop-filter` now (ADR-0310) -- what is left of that list is
            // that and `letter-spacing`.
            var style = compute("button { backdrop-filter: blur(24px); color: red }");
            assertEquals(0xFFFF0000, style.color());
        }

        @Test
        @DisplayName("a keyword that is not in the enum is dropped")
        void badKeyword() {
            assertEquals(
                    ComputedStyle.INITIAL.direction(),
                    compute("button { flex-direction: sideways }").direction());
        }

        @Test
        @DisplayName("a negative flex-grow is dropped")
        void negativeFlexGrow() {
            assertEquals(
                    ComputedStyle.INITIAL.flexGrow(),
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
            assertEquals(Insets.all(Length.points(8)), style.padding());
            assertEquals(FlexDirection.COLUMN, style.direction());
        }
    }

    @Nested
    @DisplayName("padding")
    class Padding {

        @Test
        @DisplayName("one value is every edge")
        void one() {
            assertEquals(
                    Insets.all(Length.points(12)),
                    compute("button { padding: 12px }").padding());
        }

        @Test
        @DisplayName("two values are vertical then horizontal")
        void two() {
            // The form a control is written in: `padding: 0 12px` is the button's
            // own metric, and supporting only one value would mean no control
            // could state it.
            assertEquals(
                    new Insets(Length.points(0), Length.points(12), Length.points(0), Length.points(12)),
                    compute("button { padding: 0 12px }").padding());
        }

        @Test
        @DisplayName("three values give the bottom its own, and the sides share")
        void three() {
            assertEquals(
                    new Insets(Length.points(1), Length.points(2), Length.points(3), Length.points(2)),
                    compute("button { padding: 1px 2px 3px }").padding());
        }

        @Test
        @DisplayName("four values run clockwise from the top, as CSS does")
        void four() {
            // CSS's order, not a reading order. Two orders for one concept is how
            // a padding lands on the wrong pair of edges.
            assertEquals(
                    new Insets(Length.points(1), Length.points(2), Length.points(3), Length.points(4)),
                    compute("button { padding: 1px 2px 3px 4px }").padding());
        }

        @Test
        @DisplayName("a longhand overrides one edge of the shorthand before it")
        void longhand() {
            var style = compute("button { padding: 4px; padding-left: 16px }");

            assertEquals(
                    new Insets(Length.points(4), Length.points(4), Length.points(4), Length.points(16)),
                    style.padding());
        }

        @Test
        @DisplayName("a shorthand with one bad part is dropped whole")
        void partiallyBad() {
            // Half a shorthand is harder to see than none of it: two edges would
            // move and two would not, which reads as a layout bug.
            assertEquals(
                    ComputedStyle.INITIAL.padding(),
                    compute("button { padding: 8px nonsense }").padding());
        }

        @Test
        @DisplayName("five values are not a shorthand CSS has")
        void tooMany() {
            assertEquals(
                    ComputedStyle.INITIAL.padding(),
                    compute("button { padding: 1px 2px 3px 4px 5px }").padding());
        }
    }

    /// CSS's 1-4 corner shorthand, which `group-box-title` needs and which was
    /// dropped with a warning until ADR-0216.
    @Nested
    @DisplayName("border-radius (§1.5)")
    class BorderRadius {

        private Corners corners(String value) {
            return compute("button { border-radius: " + value + " }")
                    .decoration()
                    .corners();
        }

        @Test
        @DisplayName("one value is every corner, which is every radius the system pins")
        void one() {
            assertEquals(Corners.all(8), corners("8px"));
        }

        @Test
        @DisplayName("two values are the two diagonals")
        void two() {
            assertEquals(new Corners(4, 12, 4, 12), corners("4px 12px"));
        }

        @Test
        @DisplayName("three values name the fourth as the opposite of the second")
        void three() {
            assertEquals(new Corners(1, 2, 3, 2), corners("1px 2px 3px"));
        }

        @Test
        @DisplayName("four values run clockwise from the top-left, as CSS does")
        void four() {
            // `group-box-title`'s own declaration: the frame's radius less its
            // border on top, square where the body carries on underneath.
            assertEquals(new Corners(7, 7, 0, 0), corners("7px 7px 0 0"));
        }

        @Test
        @DisplayName("a shorthand with one bad part is dropped whole")
        void partiallyBad() {
            // `padding`'s rule, for `padding`'s reason: two corners rounded and
            // two not, from a typo, reads as a drawing bug rather than a bad value.
            assertEquals(Corners.SQUARE, corners("7px nonsense"));
            assertEquals(Corners.SQUARE, corners("1px 2px 3px 4px 5px"));
        }

        @Test
        @DisplayName("a percentage is refused, because the box has no size yet")
        void percentage() {
            // The cascade runs before Yoga, so "half of this box" is a number
            // nobody has. ADR-0216 kept the answer the single radius gave.
            assertEquals(Corners.SQUARE, corners("50%"));
        }

        @Test
        @DisplayName("the elliptical form is refused, because a corner here is a circle")
        void elliptical() {
            assertEquals(Corners.SQUARE, corners("10px / 20px"));
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
            assertEquals(
                    BundledFont.Weight.SEMI_BOLD,
                    compute("button { font-weight: bold }").typography().weight());
            assertEquals(
                    BundledFont.Weight.SEMI_BOLD,
                    compute("button { font-weight: 900 }").typography().weight());
            assertEquals(
                    BundledFont.Weight.REGULAR,
                    compute("button { font-weight: 500 }").typography().weight());
            assertEquals(
                    BundledFont.Weight.REGULAR,
                    compute("button { font-weight: normal }").typography().weight());
        }

        /// `font-style`, which is a **face** rather than a decoration: the italic
        /// is drawn, so the cascade's job is to name a file — `docs/gaps.md` G27,
        /// ADR-0323.
        @Test
        @DisplayName("font-style resolves, and picks the face out of the matrix")
        void fontStyle() {
            assertEquals(
                    BundledFont.Style.ITALIC,
                    compute("button { font-style: italic }").typography().style());
            assertEquals(
                    BundledFont.UI_ITALIC,
                    compute("button { font-style: italic }").typography().face());
            assertEquals(
                    BundledFont.UI_STRONG_ITALIC,
                    compute("button { font-weight: 600; font-style: italic }")
                            .typography()
                            .face(),
                    "a semibold italic is a face, not the nearest of three");
            assertEquals(
                    BundledFont.Style.UPRIGHT,
                    compute("button { font-style: italic; font-style: normal }")
                            .typography()
                            .style());
        }

        @Test
        @DisplayName("oblique is refused, because nothing here shears a glyph")
        void obliqueIsRefused() {
            // CSS's `oblique` asks for a *slant*. Inter's italic is a different
            // drawing rather than a sheared upright, so answering with it would
            // answer a different question — and shearing would be a type-design
            // decision taken by a stylesheet. Dropped with a warning, like every
            // value outside the subset.
            assertEquals(
                    BundledFont.Style.UPRIGHT,
                    compute("button { font-style: oblique }").typography().style());
            assertEquals(
                    BundledFont.Style.UPRIGHT,
                    compute("button { font-style: oblique 14deg }").typography().style());
        }

        @Test
        @DisplayName("it inherits, because every typography component does")
        void fontStyleInherits() {
            assertEquals(
                    BundledFont.Style.ITALIC,
                    computeChild("window { font-style: italic }").typography().style());
        }

        @Test
        @DisplayName("only the first family of a list is taken")
        void noFallbackChain() {
            // §6.1 is explicit that there is no fallback cascade in v1: a
            // character outside the bundled faces is .notdef on purpose.
            // Honouring the rest of the list would pretend to a mechanism that
            // does not exist.
            assertEquals(
                    "Inter",
                    compute("button { font-family: Inter, sans-serif }")
                            .typography()
                            .family());
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
            assertEquals(
                    18, compute("button { line-height: 18px }").typography().resolvedLineHeight(), 1e-9);
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
            assertEquals(
                    ComputedStyle.INITIAL.typography().size(),
                    compute("button { font-size: 0 }").typography().size(),
                    1e-9);
            assertEquals(
                    ComputedStyle.INITIAL.typography().size(),
                    compute("button { font-size: 50% }").typography().size(),
                    1e-9);
        }
    }

    @Nested
    @DisplayName("transitions (§1.7)")
    class TransitionParsing {

        @Test
        @DisplayName("property, duration, easing and delay")
        void full() {
            var timing = compute("button { transition: background-color 100ms ease-exit 20ms }")
                    .transitions()
                    .get(Transitions.Animatable.BACKGROUND_COLOR);

            assertEquals(100, timing.durationMillis(), 1e-9);
            assertEquals(Easing.EASE_EXIT, timing.easing());
            assertEquals(20, timing.delayMillis(), 1e-9);
        }

        @Test
        @DisplayName("seconds and milliseconds both work; a bare number does not")
        void units() {
            assertEquals(
                    160,
                    compute("button { transition: opacity 0.16s }")
                            .transitions()
                            .get(Transitions.Animatable.OPACITY)
                            .durationMillis(),
                    1e-9);

            // `transition: color 200` almost certainly means milliseconds, and
            // guessing would make the one stylesheet that meant seconds silently
            // wrong.
            assertTrue(compute("button { transition: color 200 }").transitions().isEmpty());

            // Zero is the exception, because it has no duration to be wrong
            // about -- the same allowance a length gets.
            assertEquals(
                    0,
                    compute("button { transition: color 0 }")
                            .transitions()
                            .get(Transitions.Animatable.COLOR)
                            .durationMillis(),
                    1e-9);
        }

        @Test
        @DisplayName("a comma-separated list is several transitions")
        void list() {
            var transitions = compute("""
                    button { transition: background-color 100ms ease-enter,
                                         color 160ms linear }
                    """).transitions();

            assertEquals(2, transitions.byProperty().size());
            assertEquals(
                    Easing.LINEAR, transitions.get(Transitions.Animatable.COLOR).easing());
        }

        @Test
        @DisplayName("a layout property is refused, not ignored")
        void layoutPropertyRefused() {
            // §1.7: "layout properties never transition". Animating a width would
            // run Yoga every frame of every transition. An author who asked for
            // one is asking for something the system deliberately will not do,
            // and needs to be told rather than left with a rule that never fires.
            assertTrue(
                    compute("button { transition: width 200ms }").transitions().isEmpty());
            assertTrue(compute("button { transition: padding 200ms }")
                    .transitions()
                    .isEmpty());
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
            assertEquals(
                    100,
                    compute("button { transition: background 100ms }")
                            .transitions()
                            .get(Transitions.Animatable.BACKGROUND_COLOR)
                            .durationMillis(),
                    1e-9);
        }

        @Test
        @DisplayName("a curve CSS has and this system does not is refused")
        void unknownEasing() {
            assertTrue(compute("button { transition: color 100ms ease-in-out }")
                    .transitions()
                    .isEmpty());
        }

        @Test
        @DisplayName("the default curve is ease-enter")
        void defaultEasing() {
            assertEquals(
                    Easing.EASE_ENTER,
                    compute("button { transition: color 100ms }")
                            .transitions()
                            .get(Transitions.Animatable.COLOR)
                            .easing());
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

            var parent = ComputedStyle.of(resolver.resolve(root), CssLength.Context.DEFAULT);
            return ComputedStyle.of(resolver.resolve(root.descend(1)), CssLength.Context.DEFAULT, parent);
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
            assertEquals(
                    0xFF112233,
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
            assertEquals(
                    ComputedStyle.INITIAL.background(),
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
            assertEquals(
                    ComputedStyle.INITIAL.cursor(),
                    child("panel { cursor: pointer }").cursor());
        }

        @Test
        @DisplayName("the decoration does not inherit")
        void decorationDoesNot() {
            var style = child("panel { border-radius: 8px; border: 1px solid #abc }");

            assertEquals(
                    io.github.digitalsmile.goldberry.css.Corners.SQUARE,
                    style.decoration().corners());
            assertEquals(false, style.decoration().hasBorder());
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
        /// The example used to be `align-items: start`, which is the typo that
        /// produced the console flood this group exists for — and which is
        /// **accepted** now, because it is valid CSS ([ADR-0247]). A keyword
        /// nothing has is what this needs, so it asks for one nothing has.
        @Test
        @DisplayName("the declaration is dropped every time, however often it is reported")
        void alwaysDropped() {
            for (var attempt = 0; attempt < 3; attempt++) {
                var style = compute("button { align-items: sideways; gap: 4px }");
                assertEquals(
                        ComputedStyle.INITIAL.alignItems(),
                        style.alignItems(),
                        "a value this toolkit has not got must never be applied");
                assertEquals(
                        io.github.digitalsmile.goldberry.layout.Length.points(4),
                        style.gap(),
                        "and the declarations around it still are");
            }
        }
    }

    /// §8 has listed `min-width` / `max-width` from the start and nothing had
    /// needed them, so three widgets wrote a **width** where they meant a maximum
    /// and one had no minimum at all ([ADR-0181]).
    @Nested
    @DisplayName("how small and how large")
    class Limits {

        @Test
        @DisplayName("all four are read, and each lands on its own axis and end")
        void allFour() {
            var style = compute("""
                    button {
                        min-width: 320px; max-width: 640px;
                        min-height: 40px; max-height: 80px;
                    }
                    """);

            assertEquals(Length.points(320), style.limits().minWidth());
            assertEquals(Length.points(640), style.limits().maxWidth());
            assertEquals(Length.points(40), style.limits().minHeight());
            assertEquals(Length.points(80), style.limits().maxHeight());
        }

        /// The form `dialog` needs: §2 asks for "max 80% window", and a
        /// percentage is the only way to say that without measuring a window.
        @Test
        @DisplayName("a percentage stays a percentage, for the containing block to resolve")
        void percentages() {
            var style = compute("button { max-width: 80% }");

            assertEquals(Length.percent(80), style.limits().maxWidth());
        }

        /// Undefined, not zero. A minimum of zero constrains nothing, but a
        /// maximum of zero is a box that may not exist — so "no limit" cannot be
        /// spelled the same way as "a limit of none".
        @Test
        @DisplayName("a box that says nothing has no limit on any axis")
        void noneByDefault() {
            var none = ComputedStyle.INITIAL.limits();

            assertTrue(none.isNone());
            assertEquals(Length.UNDEFINED, none.maxWidth());
            assertEquals(io.github.digitalsmile.goldberry.layout.Limits.NONE, none);
        }

        /// Setting one leaves the other three alone, which is the whole reason
        /// they are one value: a caller that handled three of four would have a
        /// bug nobody would find.
        @Test
        @DisplayName("declaring one limit does not clear the others")
        void oneAtATime() {
            var style = compute("button { min-width: 320px }");

            assertEquals(Length.points(320), style.limits().minWidth());
            assertEquals(Length.UNDEFINED, style.limits().maxWidth());
            assertEquals(Length.UNDEFINED, style.limits().minHeight());
            assertFalse(style.limits().isNone());
        }

        @Test
        @DisplayName("a value that is not a length is dropped, like every other")
        void rubbishIsDropped() {
            assertEquals(
                    ComputedStyle.INITIAL.limits(),
                    compute("button { max-width: banana }").limits());
        }
    }

    @Nested
    @DisplayName("white-space, text-overflow and text-align")
    class TextFlowProperties {

        @Test
        @DisplayName("all three default to CSS's initial values")
        void initial() {
            var style = compute("input { color: red }");

            assertEquals(WhiteSpace.NORMAL, style.whiteSpace());
            assertEquals(TextOverflow.CLIP, style.textOverflow());
            assertEquals(TextAlign.START, style.textAlign());
            assertEquals(TextFlow.NORMAL, style.textFlow());
        }

        @Test
        @DisplayName("the wrapping and marking keywords are read")
        void keywordsAreRead() {
            var style = compute("button { white-space: nowrap; text-overflow: ellipsis }");

            assertEquals(WhiteSpace.NOWRAP, style.whiteSpace());
            assertEquals(TextOverflow.ELLIPSIS, style.textOverflow());
            assertEquals(TextFlow.ELLIPSIS, style.textFlow());
        }

        @Test
        @DisplayName("a value the keyword is not is dropped, like every other")
        void rubbishIsDropped() {
            assertEquals(
                    WhiteSpace.NORMAL,
                    compute("button { white-space: pre-wrap }").whiteSpace());
            assertEquals(
                    TextOverflow.CLIP, compute("button { text-overflow: fade }").textOverflow());
        }

        @Test
        @DisplayName("text-align is read, and `left` / `right` are refused for ADR-0247's reason")
        void alignmentKeywords() {
            assertEquals(TextAlign.END, compute("button { text-align: end }").textAlign());
            assertEquals(
                    TextAlign.CENTER, compute("button { text-align: center }").textAlign());
            assertEquals(
                    TextAlign.START, compute("button { text-align: start }").textAlign());

            // Not aliases. They coincide with `start`/`end` under LTR and part
            // company under RTL, so accepting them would write down an answer
            // that is right today and silently wrong later. `justify` is refused
            // for a different reason: it is a respacing, and a paragraph shaped
            // once has nowhere to put the extra advance.
            assertEquals(
                    TextAlign.START, compute("button { text-align: right }").textAlign());
            assertEquals(TextAlign.START, compute("button { text-align: left }").textAlign());
            assertEquals(
                    TextAlign.START, compute("button { text-align: justify }").textAlign());
        }

        /// The one thing that makes them two properties rather than one value.
        @Test
        @DisplayName("white-space and text-align inherit; text-overflow does not")
        void onlyTheCssInheritedHalvesInherit() {
            var child = computeChild("window { white-space: nowrap; text-overflow: ellipsis; text-align: end }");

            assertEquals(WhiteSpace.NOWRAP, child.whiteSpace(), "`menu { white-space: nowrap }` is about the rows");
            assertEquals(TextAlign.END, child.textAlign(), "and a column's alignment is about its cells");
            assertEquals(
                    TextOverflow.CLIP,
                    child.textOverflow(),
                    "a container that draws no text would otherwise mark every label under it");
        }

        @Test
        @DisplayName("a child says so for itself, over anything it inherited")
        void aChildMayOverride() {
            var child = computeChild("window { white-space: nowrap } button { white-space: normal }");

            assertEquals(WhiteSpace.NORMAL, child.whiteSpace());
        }

        /// `inheritsSameAs` is the style cache's key, and the note on it says a
        /// property that starts inheriting has to be added to both it and
        /// `inheritingFrom` or the cache goes stale rather than merely cold.
        @Test
        @DisplayName("two parents that differ only in white-space are not the same to a child")
        void whiteSpaceIsPartOfTheInheritedKey() {
            var wrapping = ComputedStyle.INITIAL;
            var nowrap = ComputedStyle.INITIAL.whiteSpace(WhiteSpace.NOWRAP);

            assertFalse(wrapping.inheritsSameAs(nowrap));
            assertFalse(wrapping.inheritsSameAs(ComputedStyle.INITIAL.textAlign(TextAlign.END)));
            assertTrue(wrapping.inheritsSameAs(ComputedStyle.INITIAL.textOverflow(TextOverflow.ELLIPSIS)));
        }
    }

    /// `text-decoration`, the fourth property on the same value — `docs/gaps.md`
    /// G27, ADR-0321.
    @Nested
    @DisplayName("text-decoration")
    class Decorations {

        @Test
        @DisplayName("nothing is decorated unless something says so")
        void initial() {
            var style = compute("input { color: red }");

            assertEquals(TextDecoration.NONE, style.textDecoration());
            assertFalse(style.textFlow().isDecorated(), "an undecorated flow must not make the painter read metrics");
        }

        @Test
        @DisplayName("both lines are read, together and separately, under either spelling")
        void keywordsAreRead() {
            assertEquals(
                    Set.of(TextDecoration.UNDERLINE),
                    compute("button { text-decoration: underline }").textDecoration());
            assertEquals(
                    Set.of(TextDecoration.LINE_THROUGH),
                    compute("button { text-decoration-line: line-through }").textDecoration());
            assertEquals(
                    Set.of(TextDecoration.UNDERLINE, TextDecoration.LINE_THROUGH),
                    compute("button { text-decoration: underline line-through }")
                            .textDecoration());
            assertEquals(
                    TextDecoration.NONE,
                    compute("button { text-decoration: underline; text-decoration: none }")
                            .textDecoration());
        }

        /// The shorthand's other two parts. Dropping the declaration whole is the
        /// point: a rule that asked for a wavy red underline and got a straight one
        /// in the text's colour would be a property that lies.
        @Test
        @DisplayName("a colour or a style in the shorthand drops the whole declaration")
        void therestOfTheShorthandIsRefused() {
            assertEquals(
                    TextDecoration.NONE,
                    compute("button { text-decoration: underline wavy }").textDecoration());
            assertEquals(
                    TextDecoration.NONE,
                    compute("button { text-decoration: underline red }").textDecoration());
            assertEquals(
                    TextDecoration.NONE,
                    compute("button { text-decoration: overline }").textDecoration(),
                    "`overline` is CSS's and is not in the subset");
            assertEquals(
                    TextDecoration.NONE,
                    compute("button { text-decoration: underline underline }").textDecoration(),
                    "named twice is a declaration nobody can read back");
            assertEquals(
                    TextDecoration.NONE,
                    compute("button { text-decoration: none underline }").textDecoration(),
                    "a contradiction is refused rather than guessed at");
        }

        /// It inherits, which is how CSS's *propagation* to in-flow descendants
        /// reads here: a control's text is very often an anonymous child box.
        @Test
        @DisplayName("it reaches the label inside the node that asked for it")
        void itInherits() {
            var child = computeChild("window { text-decoration: underline }");

            assertEquals(Set.of(TextDecoration.UNDERLINE), child.textDecoration());
            assertTrue(child.textFlow().has(TextDecoration.UNDERLINE));
        }

        @Test
        @DisplayName("and a child may say otherwise")
        void aChildMayOverride() {
            var child = computeChild("window { text-decoration: underline } button { text-decoration: none }");

            assertEquals(TextDecoration.NONE, child.textDecoration());
        }

        /// The other half of inheriting: the style cache's key has to know about it,
        /// or a child under a newly underlined parent keeps the style it resolved
        /// before.
        @Test
        @DisplayName("two parents that differ only in it are not the same to a child")
        void itIsPartOfTheInheritedKey() {
            var plain = ComputedStyle.INITIAL;
            var underlined = ComputedStyle.INITIAL.textDecoration(Set.of(TextDecoration.UNDERLINE));

            assertFalse(plain.inheritsSameAs(underlined));
            assertTrue(
                    underlined.inheritsSameAs(ComputedStyle.INITIAL.textDecoration(Set.of(TextDecoration.UNDERLINE))));
        }

        @Test
        @DisplayName("a style is a value, so the set it carries cannot be changed underneath it")
        void theSetIsCopied() {
            var mutable = new java.util.LinkedHashSet<TextDecoration>();
            mutable.add(TextDecoration.UNDERLINE);
            var style = ComputedStyle.INITIAL.textDecoration(mutable);

            mutable.add(TextDecoration.LINE_THROUGH);

            assertEquals(Set.of(TextDecoration.UNDERLINE), style.textDecoration());
        }
    }
}
