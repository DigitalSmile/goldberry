package dev.goldberry.css;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.Border.Line;
import dev.goldberry.css.Border.Side;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.lint.Finding;
import dev.goldberry.css.lint.StyleLint;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.Shadow;

/// A border per side through the cascade: a border has four sides.
///
/// What is asserted is the [Border] a rule resolves to, side by side, because the
/// whole content of the feature is which written value reaches which side: a
/// transposition between `right` and `left` would look like a painter bug.
class BorderStyleTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    /// The whole pipeline — parse, cascade, compute — for a `button.quiet`.
    private static Border borderOf(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var root = element("window");
        root.with(element("button.quiet"));
        var declarations = new StyleResolver(List.of(sheet)).resolve(root.descend(1));
        return ComputedStyle.of(declarations, CssLength.Context.DEFAULT)
                .decoration()
                .border();
    }

    private static Border declared(String declarations) {
        return borderOf("button { " + declarations + " }");
    }

    /// The properties the lint reports as doing nothing — the same question
    /// `SupportedPropertyTest` asks of the toolkit's own sheets.
    private static List<String> dead(String declarations) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { " + declarations + " }");
        return new StyleLint(List.of(sheet))
                .check(sheet).stream()
                        .filter(finding -> finding.kind() == Finding.Kind.DEAD_DECLARATION)
                        .map(Finding::property)
                        .toList();
    }

    private static Border sides(Line top, Line right, Line bottom, Line left) {
        return new Border(top, right, bottom, left);
    }

    @Nested
    @DisplayName("one side")
    class OneSide {

        @ParameterizedTest
        @ValueSource(strings = {"top", "right", "bottom", "left"})
        @DisplayName("each shorthand reaches its own side and no other")
        void everyShorthandIsWired(String side) {
            var border = declared("border-" + side + ": 3px solid #ff0000");

            var expected = Border.NONE.side(Side.valueOf(side.toUpperCase(Locale.ROOT)), new Line(3, RED));
            assertEquals(expected, border);
        }

        @ParameterizedTest
        @ValueSource(strings = {"top", "right", "bottom", "left"})
        @DisplayName("and each width and colour longhand too")
        void everyLonghandIsWired(String side) {
            var border = declared("border-" + side + "-width: 2px; border-" + side + "-color: #0000ff");

            var expected = Border.NONE.side(Side.valueOf(side.toUpperCase(Locale.ROOT)), new Line(2, BLUE));
            assertEquals(expected, border);
        }

        @Test
        @DisplayName("the style keyword is read and drawn solid, in any order, as `border` has always done")
        void styleIsAWord() {
            assertEquals(
                    new Line(1, RED),
                    declared("border-bottom: dashed #ff0000 1px").bottom());
        }

        @Test
        @DisplayName("`none` is a zero width")
        void none() {
            assertEquals(
                    Line.NONE,
                    declared("border: 1px solid #ff0000; border-left: none").left());
        }

        @Test
        @DisplayName("a side's shorthand resets what it does not name, on that side only")
        void shorthandResets() {
            // `border: red` after `border: 2px solid blue` is a 0px border, and
            // this is the same sentence about one side.
            var border = declared("border: 2px solid #0000ff; border-left: #ff0000");

            assertEquals(new Line(0, RED), border.left());
            assertEquals(new Line(2, BLUE), border.top(), "the other three are untouched");
        }
    }

    @Nested
    @DisplayName("the four together")
    class AllFour {

        /// CSS's 1-to-4 expansion, in `padding`'s order.
        @ParameterizedTest(name = "{0}")
        @CsvSource({
            // declaration, top, right, bottom, left
            "border-width: 1px,             1, 1, 1, 1",
            "border-width: 1px 2px,         1, 2, 1, 2",
            "border-width: 1px 2px 3px,     1, 2, 3, 2",
            "border-width: 1px 2px 3px 4px, 1, 2, 3, 4",
        })
        @DisplayName("`border-width` takes one to four values")
        void widths(String declaration, double top, double right, double bottom, double left) {
            var border = declared("border-color: #ff0000; " + declaration);

            assertEquals(
                    sides(new Line(top, RED), new Line(right, RED), new Line(bottom, RED), new Line(left, RED)),
                    border);
        }

        @Test
        @DisplayName("and `border-color` takes one to four colours, a function counting as one")
        void colours() {
            var border = declared("border-width: 1px; border-color: #ff0000 rgba(0, 0, 255, 1)");

            assertEquals(sides(new Line(1, RED), new Line(1, BLUE), new Line(1, RED), new Line(1, BLUE)), border);
        }

        @Test
        @DisplayName("one value is every side, which is uniform and draws as it always has")
        void oneIsUniform() {
            var border = declared("border: 1px solid #ff0000");

            assertEquals(Border.all(1, RED), border);
            assertTrue(border.isUniform());
        }

        @Test
        @DisplayName("a box that declares no border has none")
        void initial() {
            assertSame(Border.NONE, ComputedStyle.INITIAL.decoration().border());
            assertFalse(ComputedStyle.INITIAL.decoration().hasBorder());
        }
    }

    @Nested
    @DisplayName("the cascade, where later wins per side")
    class Cascade {

        @Test
        @DisplayName("a side after `border` replaces that side and leaves the other three")
        void sideAfterShorthand() {
            var border = declared("border: 1px solid #ff0000; border-left: 4px solid #0000ff");

            assertEquals(sides(new Line(1, RED), new Line(1, RED), new Line(1, RED), new Line(4, BLUE)), border);
            assertFalse(border.isUniform());
        }

        @Test
        @DisplayName("and `border` after a side resets all four, which is what a shorthand is")
        void shorthandAfterSide() {
            assertEquals(Border.all(1, RED), declared("border-left: 4px solid #0000ff; border: 1px solid #ff0000"));
        }

        @Test
        @DisplayName("a longhand after a side's shorthand changes only its own half")
        void longhandAfterSide() {
            assertEquals(
                    new Line(3, RED),
                    declared("border-top: 1px solid #ff0000; border-top-width: 3px")
                            .top());
        }

        @Test
        @DisplayName("across rules it is the winning rule that is applied last, not the one written last")
        void specificityDecidesTheOrder() {
            // `button.quiet` is more specific than `button`, so its `border` wins
            // and is applied after the less specific rule's `border-left`, even
            // though the side is written later in the sheet: winners are applied
            // in cascade order, not source order.
            var border = borderOf("""
                    button.quiet { border: 1px solid #ff0000 }
                    button { border-left: 4px solid #0000ff }
                    """);

            assertEquals(Border.all(1, RED), border);
        }

        @Test
        @DisplayName("and at equal specificity the later rule's side wins over the earlier rule's shorthand")
        void laterRuleWins() {
            var border = borderOf("""
                    button { border: 1px solid #ff0000 }
                    button { border-bottom: 2px solid #0000ff }
                    """);

            assertEquals(new Line(2, BLUE), border.bottom());
            assertEquals(new Line(1, RED), border.top());
        }
    }

    @Nested
    @DisplayName("what the lint reports, as it did before")
    class Refusals {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "border-left: 50% solid #ff0000",
                    "border-left: 1px solid nonsense",
                    "border-top-width: 10%",
                    "border-bottom-color: 3px",
                    "border-width: 1px nonsense",
                    "border-width: 1px 2px 3px 4px 5px",
                    "border-color: #ff0000 nonsense"
                })
        @DisplayName("a value the engine would not take is reported, and the border is untouched")
        void badValues(String declaration) {
            var property = declaration.substring(0, declaration.indexOf(':'));

            assertEquals(List.of(property), dead(declaration), declaration);
            assertEquals(Border.all(1, RED), declared("border: 1px solid #ff0000; " + declaration), declaration);
        }

        @ParameterizedTest
        @ValueSource(strings = {"border-style: dashed", "border-left-style: dashed"})
        @DisplayName("a `-style` longhand is not a property: style is a word inside a shorthand")
        void noStyleLonghands(String declaration) {
            var property = declaration.substring(0, declaration.indexOf(':'));

            assertEquals(List.of(property), dead(declaration));
        }

        @Test
        @DisplayName("and every per-side declaration the toolkit writes is one the lint passes")
        void theNewPropertiesPass() {
            assertTrue(dead("border-top: 1px solid #ff0000; border-right-width: 2px; border-bottom-color: #ff0000;"
                            + " border-left: none; border-width: 1px 2px; border-color: #ff0000 #0000ff")
                    .isEmpty());
        }
    }

    @Nested
    @DisplayName("the value itself")
    class Value {

        @Test
        @DisplayName("fading scales every side's alpha and no width")
        void fade() {
            var border = sides(new Line(1, RED), new Line(2, BLUE), Line.NONE, new Line(4, RED))
                    .fade(0.5);

            assertEquals(0x80, border.top().argb() >>> 24);
            assertEquals(0x80, border.right().argb() >>> 24);
            assertEquals(2, border.right().width());
            assertEquals(4, border.left().width());
        }

        @Test
        @DisplayName("a negative width is clamped rather than refused, for Decoration's reason")
        void clamped() {
            assertEquals(0, new Line(-3, RED).width());
            assertFalse(new Line(0, RED).hasInk());
            assertFalse(new Line(2, 0x00FF0000).hasInk(), "a transparent side draws nothing either");
        }

        @Test
        @DisplayName("the uniform constructor Decoration kept is the four-sided one with one line")
        void uniformDecoration() {
            var decoration = new Decoration(Corners.SQUARE, 2, RED, 0, 0, 0, Shadow.NONE);

            assertEquals(Border.all(2, RED), decoration.border());
        }
    }
}
