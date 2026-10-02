package dev.goldberry.css;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Align;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Wrap;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.render.model.LogicalRect;

/// The `flex` shorthand, `row-gap` and `column-gap`, and the question a
/// stylesheet asks to warn about a property the engine has not got.
class FlexAndGapShorthandTest {

    private static ComputedStyle compute(String declarations) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "tile { " + declarations + " }");
        var declared = new StyleResolver(List.of(sheet)).resolve(element("tile"));
        return ComputedStyle.of(declared, CssLength.Context.DEFAULT);
    }

    @Nested
    @DisplayName("the flex shorthand")
    class Flex {

        @ParameterizedTest(name = "flex: {0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "none      | 0 | 0 | auto",
                    "auto      | 1 | 1 | auto",
                    "initial   | 0 | 1 | auto",
                    "1         | 1 | 1 | 0%",
                    "2 3       | 2 | 3 | 0%",
                    "2 120px   | 2 | 1 | 120px",
                    "120px     | 1 | 1 | 120px",
                    "0         | 0 | 1 | 0%",
                    "1 0 50%   | 1 | 0 | 50%",
                })
        @DisplayName("expands as CSS says, which is not what the numbers suggest alone")
        void expands(String written, double grow, double shrink, String basis) {
            var style = compute("flex: " + written);
            assertEquals(grow, style.flexGrow());
            assertEquals(shrink, style.flexShrink());
            assertEquals(basis(basis), style.flexBasis());
        }

        private static Length basis(String text) {
            if (text.equals("auto")) {
                return Length.AUTO;
            }
            if (text.endsWith("%")) {
                return Length.percent(Float.parseFloat(text.substring(0, text.length() - 1)));
            }
            return Length.points(Float.parseFloat(text.substring(0, text.length() - 2)));
        }

        @Test
        @DisplayName("a later longhand overrides the part of the shorthand it names")
        void longhandAfterShorthand() {
            var style = compute("flex: 1; flex-basis: 40px");
            assertEquals(1, style.flexGrow());
            assertEquals(Length.points(40), style.flexBasis());
        }
    }

    @Nested
    @DisplayName("row-gap and column-gap")
    class Gaps {

        @Test
        @DisplayName("gap with one length sets both, with two the row gap then the column gap")
        void gapShorthand() {
            var both = compute("gap: 8px");
            assertEquals(Length.points(8), both.rowGap());
            assertEquals(Length.points(8), both.columnGap());
            assertEquals(Length.points(8), both.gap());

            var two = compute("gap: 4px 12px");
            assertEquals(Length.points(4), two.rowGap());
            assertEquals(Length.points(12), two.columnGap());
        }

        @Test
        @DisplayName("each longhand sets its own gutter and leaves the other")
        void longhands() {
            var style = compute("gap: 8px; column-gap: 20px");
            assertEquals(Length.points(8), style.rowGap());
            assertEquals(Length.points(20), style.columnGap());
            assertEquals(Length.points(3), compute("row-gap: 3px").rowGap());
            assertEquals(Length.points(0), compute("row-gap: 3px").columnGap());
        }

        @Test
        @DisplayName("the box hands Yoga both gutters, so a wrapping row spaces lines and items apart differently")
        void yogaGetsBothGutters() {
            RendererRequirement.enforce();
            var target = TestFrames.of(200, 200, 1.0f);
            try (var tree = RenderTree.create()) {
                var row = Box.of()
                        .size(Length.points(100), Length.points(200))
                        .direction(FlexDirection.ROW)
                        .wrap(Wrap.WRAP)
                        .style(ComputedStyle.INITIAL
                                .direction(FlexDirection.ROW)
                                .wrap(Wrap.WRAP)
                                // Lines packed at the top, so the second sits
                                // exactly one row gap under the first.
                                .alignContent(Align.FLEX_START)
                                .width(Length.points(100))
                                .height(Length.points(200))
                                .rowGap(Length.points(7))
                                .columnGap(Length.points(10)))
                        .children(cell(), cell(), cell());
                tree.update(target.frame(), row);
                var placed = new ArrayList<LogicalRect>();
                tree.forEachPlacedBox(box -> placed.add(box.layout()));
                // The row itself, then three 40-wide cells: two fit on the first
                // line with a 10px column gap, the third wraps 7px below.
                assertEquals(50, placed.get(2).left(), 0.01, "the second cell sits a column gap after the first");
                assertEquals(0, placed.get(3).left(), 0.01, "the third wraps to the start");
                assertEquals(27, placed.get(3).top(), 0.01, "a row gap below the first line");
            } finally {
                target.end();
            }
        }

        private static Box cell() {
            return Box.filled(0xFF00FF00).size(Length.points(40), Length.points(20));
        }
    }

    @Nested
    @DisplayName("whether a property exists at all")
    class Properties {

        @Test
        @DisplayName("every property the engine reads is one, whatever its value")
        void knownProperties() {
            for (var property : List.of(
                    "color", "gap", "row-gap", "column-gap", "flex", "white-space", "overflow-wrap", "word-break")) {
                assertTrue(ComputedStyle.isProperty(property), property);
            }
        }

        @Test
        @DisplayName("and a property the subset has not got is not, which is what a sheet warns about once")
        void unknownProperties() {
            for (var property : List.of("letter-spacing", "backdrop-filter", "z-index", "--gb-accent")) {
                assertFalse(ComputedStyle.isProperty(property), property);
            }
        }
    }
}
