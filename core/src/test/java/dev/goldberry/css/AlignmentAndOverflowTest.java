package dev.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Align;
import dev.goldberry.layout.Justify;
import dev.goldberry.layout.Overflow;

/// `place-*`, `justify-items` and `-self`, `justify-content: stretch`, and
/// overflow per axis.
class AlignmentAndOverflowTest {

    private static Map<String, List<Token>> declarations(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        var rule = CssParser.parseSheet("probe { " + css + " }", ParseMode.LENIENT, "AlignmentAndOverflowTest")
                .rules()
                .getFirst();
        for (var declaration : rule.declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return map;
    }

    private static ComputedStyle style(String css) {
        return ComputedStyle.INITIAL.applied(declarations(css), CssLength.Context.DEFAULT);
    }

    @Nested
    @DisplayName("place-*")
    class Place {

        @Test
        @DisplayName("one value is both axes, two are the alignment then the justification")
        void content() {
            var one = style("place-content: center");
            var two = style("place-content: flex-end space-between");

            assertEquals(Align.CENTER, one.alignContent());
            assertEquals(Justify.CENTER, one.justifyContent());
            assertEquals(Align.FLEX_END, two.alignContent());
            assertEquals(Justify.SPACE_BETWEEN, two.justifyContent());
        }

        @Test
        @DisplayName("items and self set the alignment and check the justification")
        void itemsAndSelf() {
            assertEquals(Align.CENTER, style("place-items: center").alignItems());
            assertEquals(Align.FLEX_END, style("place-self: flex-end auto").alignSelf());
            assertEquals(ComputedStyle.INITIAL, style("place-items: center nonsense"), "a bad half drops it whole");
        }
    }

    @Nested
    @DisplayName("justify-*")
    class Justification {

        @ParameterizedTest
        @ValueSource(strings = {"center", "safe end", "first baseline", "legacy left", "normal"})
        @DisplayName("justify-items reads CSS's values and changes nothing on a flex item")
        void items(String value) {
            assertEquals(ComputedStyle.INITIAL, style("justify-items: " + value));
            assertEquals(
                    true,
                    ComputedStyle.applies(
                            "justify-items",
                            declarations("justify-items: " + value).get("justify-items"),
                            CssLength.Context.DEFAULT));
        }

        @ParameterizedTest
        @ValueSource(strings = {"auto legacy", "sideways", "safe stretch"})
        @DisplayName("and refuses what CSS refuses, so a typo is still caught")
        void refused(String value) {
            assertEquals(
                    false,
                    ComputedStyle.applies(
                            "justify-self",
                            declarations("justify-self: " + value).get("justify-self"),
                            CssLength.Context.DEFAULT));
        }

        @Test
        @DisplayName("justify-content: stretch is the start, as on any flex container")
        void stretch() {
            assertEquals(
                    Justify.FLEX_START,
                    style("justify-content: center; justify-content: stretch").justifyContent());
        }
    }

    @Nested
    @DisplayName("overflow")
    class Axes {

        @Test
        @DisplayName("is one value for both axes, or across then down")
        void shorthand() {
            var both = style("overflow: hidden");
            var two = style("overflow: clip scroll");

            assertEquals(Overflow.HIDDEN, both.overflowX());
            assertEquals(Overflow.HIDDEN, both.overflowY());
            assertEquals(Overflow.CLIP, two.overflowX());
            assertEquals(Overflow.SCROLL, two.overflowY());
        }

        @Test
        @DisplayName("each axis is set by itself, and the box gets the one that cuts most")
        void joined() {
            var style = style("overflow-x: hidden; overflow-y: auto");

            assertEquals(Overflow.HIDDEN, style.overflowX());
            assertEquals(Overflow.SCROLL, style.overflowY());
            assertEquals(Overflow.SCROLL, style.overflow());
            assertEquals(Overflow.CLIP, style("overflow-y: clip").overflow());
            assertEquals(Overflow.VISIBLE, ComputedStyle.INITIAL.overflow());
        }
    }
}
