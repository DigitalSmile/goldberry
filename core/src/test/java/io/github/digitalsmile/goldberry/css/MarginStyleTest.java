package io.github.digitalsmile.goldberry.css;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;

/// `margin` through the cascade, and the `auto` that four other properties may
/// not have — ADR-0311.
class MarginStyleTest {

    /// The whole pipeline: parse, cascade, substitute, compute.
    private static ComputedStyle compute(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var root = element("window");
        root.with(element("button"));
        var declarations = new StyleResolver(List.of(sheet)).resolve(root.descend(1));
        return ComputedStyle.of(declarations, CssLength.Context.DEFAULT);
    }

    private static Insets marginOf(String declarations) {
        return compute("button { " + declarations + " }").margin();
    }

    private static Length px(float value) {
        return Length.points(value);
    }

    @Nested
    @DisplayName("the shorthand")
    class Shorthand {

        /// CSS's 1-to-4 expansion, whose whole content is which written value
        /// reaches which edge — so the edges are named in the row rather than
        /// built by `Insets.all` and `Insets.symmetric`, which would let the same
        /// transposition through twice.
        @ParameterizedTest(name = "{0}")
        @CsvSource({
            // declaration, top, right, bottom, left
            "margin: 8px,             8, 8, 8, 8",
            "margin: 4px 12px,        4, 12, 4, 12",
            "margin: 1px 2px 3px,     1, 2, 3, 2",
            "margin: 1px 2px 3px 4px, 1, 2, 3, 4",
        })
        @DisplayName(
                "one value is every edge, two are vertical then horizontal, three name the fourth, four go clockwise")
        void expansion(String declaration, float top, float right, float bottom, float left) {
            assertEquals(new Insets(px(top), px(right), px(bottom), px(left)), marginOf(declaration));
        }

        @Test
        @DisplayName("a longhand after the shorthand sets one edge and leaves the other three")
        void longhand() {
            assertEquals(new Insets(px(8), px(8), px(8), px(20)), marginOf("margin: 8px; margin-left: 20px"));
        }

        @Test
        @DisplayName("and a shorthand after the longhand resets all four, which is what a shorthand is")
        void shorthandWinsWhenItIsLast() {
            assertEquals(Insets.all(px(8)), marginOf("margin-left: 20px; margin: 8px"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"margin-top", "margin-right", "margin-bottom", "margin-left"})
        @DisplayName("each longhand reaches its own edge and no other")
        void everyLonghandIsWired(String property) {
            // The transcription bug `edgeOf` exists to prevent: four names
            // mapping onto four slots, where a swap looks like a layout bug.
            var margin = marginOf(property + ": 9px");
            var edges = List.of(margin.top(), margin.right(), margin.bottom(), margin.left());

            assertEquals(1, edges.stream().filter(px(9)::equals).count(), property + " set the wrong count of edges");
        }

        @Test
        @DisplayName("a negative margin survives, because it means something")
        void negative() {
            assertEquals(Insets.all(px(-4)), marginOf("margin: -4px"));
        }

        @Test
        @DisplayName("`em` resolves against the font size in force")
        void relative() {
            assertEquals(Insets.all(px(26)), marginOf("font-size: 13px; margin: 2em"));
        }

        @Test
        @DisplayName("a box that declares no margin has none")
        void initial() {
            assertSame(Insets.ZERO, ComputedStyle.INITIAL.margin());
            assertEquals(Insets.ZERO, marginOf("color: red"));
        }

        @Test
        @DisplayName("a half-parseable shorthand is dropped whole")
        void partial() {
            // `padding`'s rule, and for its reason: a shorthand applied to two
            // edges out of four is harder to see than one that did nothing.
            assertEquals(Insets.ZERO, marginOf("margin: 8px nonsense"));
        }
    }

    @Nested
    @DisplayName("`auto`")
    class Auto {

        @Test
        @DisplayName("`margin: 0 auto` resolves to auto on the two horizontal edges")
        void centred() {
            assertEquals(new Insets(px(0), Length.AUTO, px(0), Length.AUTO), marginOf("margin: 0 auto"));
        }

        @Test
        @DisplayName("and a longhand takes it too")
        void longhand() {
            assertEquals(Length.AUTO, marginOf("margin-left: auto").left());
        }

        /// The crash this closes.
        ///
        /// Yoga's setters come in pairs and four of them have no `auto` half —
        /// there is no `YGNodeStyleSetPaddingAuto`. `Yoga` binds those without
        /// it and refuses an `auto` **by name**, which is right there and made
        /// `padding: auto` in a stylesheet an exception thrown in the middle of a
        /// layout pass: a window closing over one typo. §8's rule for a value the
        /// engine cannot honour is to drop the declaration, and this is where
        /// that has to happen.
        @ParameterizedTest
        @ValueSource(
                strings = {
                    "padding: auto",
                    "padding: 8px auto",
                    "padding-left: auto",
                    "inset: auto",
                    "top: auto",
                    "gap: auto",
                    "min-width: auto",
                    "max-width: auto",
                    "min-height: auto",
                    "max-height: auto"
                })
        @DisplayName("is dropped by the properties the layout engine has no auto call for")
        void refusedWhereItWouldThrow(String declaration) {
            var style = compute("button { " + declaration + "; color: #eceff4 }");

            assertEquals(ComputedStyle.INITIAL.padding(), style.padding(), declaration);
            assertEquals(ComputedStyle.INITIAL.inset(), style.inset(), declaration);
            assertEquals(ComputedStyle.INITIAL.gap(), style.gap(), declaration);
            assertEquals(ComputedStyle.INITIAL.limits(), style.limits(), declaration);
            assertEquals(0xFFECEFF4, style.color(), "and the rest of the node survives");
        }

        @Test
        @DisplayName("but `width` and `height` keep it, because Yoga has the call")
        void allowedWhereItWorks() {
            // `auto` is a width's *default* and the only way a rule undoes a more
            // specific one that set a number.
            var style = compute("button { width: auto; height: auto }");

            assertSame(Length.AUTO, style.width());
            assertSame(Length.AUTO, style.height());
        }
    }
}
