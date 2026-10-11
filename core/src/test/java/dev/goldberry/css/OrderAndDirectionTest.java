package dev.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Direction;
import dev.goldberry.layout.Length;
import dev.goldberry.text.flow.TextAlign;

/// `order` and `direction`: where a box is placed among its siblings, and
/// which way its inline axis runs.
class OrderAndDirectionTest {

    private static Map<String, List<Token>> declarations(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        var rule = CssParser.parseSheet("probe { " + css + " }", ParseMode.LENIENT, "OrderAndDirectionTest")
                .rules()
                .getFirst();
        for (var declaration : rule.declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return map;
    }

    private static ComputedStyle style(String css, ComputedStyle parent) {
        return ComputedStyle.of(declarations(css), CssLength.Context.DEFAULT, parent);
    }

    /// A parent whose values differ from the initial style, so that a value
    /// that was not applied cannot pass for one that was.
    private static final ComputedStyle PARENT =
            ComputedStyle.of(declarations("order: 7; direction: rtl"), CssLength.Context.DEFAULT);

    @Nested
    @DisplayName("order")
    class Order {

        @ParameterizedTest(name = "{0}")
        @CsvSource({"order: 2, 2", "order: -1, -1", "order: 0, 0", "order: calc(1 + 2), 3"})
        @DisplayName("is a whole number, negative allowed")
        void applied(String css, int expected) {
            assertEquals(expected, style(css, PARENT).presence().order());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"order: 1.5", "order: auto", "order: 10%", "order: 2px"})
        @DisplayName("drops anything else, keeping the initial 0")
        void dropped(String css) {
            assertEquals(0, style(css, PARENT).presence().order());
        }

        @Test
        @DisplayName("does not inherit")
        void notInherited() {
            assertEquals(7, PARENT.presence().order());
            assertEquals(0, style("", PARENT).presence().order());
        }
    }

    @Nested
    @DisplayName("direction")
    class DirectionProperty {

        @Test
        @DisplayName("inherits, and a child may say otherwise")
        void inherits() {
            assertEquals(Direction.RTL, style("", PARENT).presence().direction());
            assertEquals(
                    Direction.LTR, style("direction: ltr", PARENT).presence().direction());
            assertEquals(Direction.LTR, ComputedStyle.INITIAL.presence().direction());
        }

        @Test
        @DisplayName("drops a keyword it has not got")
        void dropped() {
            assertEquals(
                    Direction.RTL, style("direction: up", PARENT).presence().direction());
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "direction: rtl; margin-inline-start: 4px | 0 | 4",
                    "direction: rtl; margin-inline-end: 4px   | 4 | 0",
                    "direction: rtl; margin-inline: 1px 2px   | 2 | 1",
                    "margin-inline-start: 4px; direction: rtl | 0 | 4",
                    "margin-inline-start: 4px                 | 4 | 0",
                })
        @DisplayName("turns the inline start to the right, wherever the direction is written")
        void logicalProperties(String css, float left, float right) {
            var rtl = style(css, ComputedStyle.INITIAL);
            assertEquals(Length.points(left), rtl.margin().left(), css);
            assertEquals(Length.points(right), rtl.margin().right(), css);
        }

        @Test
        @DisplayName("a global keyword on a logical property reaches the side the direction names")
        void keywords() {
            var parent = style("margin-right: 9px; margin-left: 1px", ComputedStyle.INITIAL);
            var child = style("direction: rtl; margin-inline-start: inherit", parent);
            assertEquals(Length.points(9), child.margin().right());
            assertEquals(Length.points(0), child.margin().left());
        }

        @Test
        @DisplayName("leaves the block axis and the sizes alone")
        void blockAxis() {
            var rtl = style("direction: rtl; padding-block: 1px 2px; inline-size: 30px", ComputedStyle.INITIAL);
            assertEquals(Length.points(1), rtl.padding().top());
            assertEquals(Length.points(2), rtl.padding().bottom());
            assertEquals(Length.points(30), rtl.width());
        }

        @Test
        @DisplayName("an inherited direction mirrors a child's logical properties too")
        void inheritedMirrors() {
            var child = style("padding-inline-start: 5px", PARENT);
            assertEquals(Length.points(5), child.padding().right());
            assertEquals(Length.points(0), child.padding().left());
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"START, RIGHT", "END, LEFT", "LEFT, LEFT", "RIGHT, RIGHT", "CENTER, CENTER"})
        @DisplayName("places start and end at the sides they are for it")
        void lineAlign(TextAlign keyword, TextAlign placed) {
            assertEquals(placed, keyword.in(Direction.RTL));
            assertEquals(keyword, keyword.in(Direction.LTR));
        }

        @Test
        @DisplayName("the keyword inherits, and each box places it by its own direction")
        void keywordInherits() {
            var rtl = style("text-align: start", PARENT);
            assertEquals(TextAlign.RIGHT, rtl.lineAlign());
            assertEquals(TextAlign.RIGHT, rtl.textFlow().textAlign());
            var ltrChild = style("direction: ltr", rtl);
            assertEquals(TextAlign.START, ltrChild.textAlign());
            assertEquals(TextAlign.START, ltrChild.lineAlign());
        }
    }

    @Test
    @DisplayName("a strict sheet refuses neither, a value outside them is dropped in either mode")
    void parseModes() {
        CssParser.parseSheet("a { order: 2; direction: rtl }", ParseMode.STRICT, "t");
        assertEquals(
                1,
                CssParser.parseSheet("a { order: 2; direction: sideways }", ParseMode.LENIENT, "t")
                        .rules()
                        .size());
        assertThrows(
                CssSyntaxException.class,
                () -> CssParser.parseSheet("a { order: }", ParseMode.STRICT, "t"),
                "a declaration with no value is malformed");
    }
}
