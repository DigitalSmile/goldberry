package io.github.digitalsmile.goldberry.css;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.Justify;
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
            assertEquals(StyleLength.points(8), style.padding());
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
            assertEquals(StyleLength.points(30), style.padding());
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
            assertEquals(StyleLength.points(32), style.padding());
        }

        @Test
        @DisplayName("a unitless zero is a length; any other unitless number is not")
        void unitlessZero() {
            assertEquals(StyleLength.points(0), compute("button { padding: 0 }").padding());
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
            assertEquals(StyleLength.points(8), style.padding());
            assertEquals(FlexDirection.COLUMN, style.direction());
        }
    }
}
