package dev.goldberry.css.value.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.calc.CalcNode.Clamp;
import dev.goldberry.css.value.calc.CalcNode.Dimension;
import dev.goldberry.css.value.calc.CalcNode.Invert;
import dev.goldberry.css.value.calc.CalcNode.Negate;
import dev.goldberry.css.value.calc.CalcNode.Percentage;
import dev.goldberry.css.value.calc.CalcNode.Plain;
import dev.goldberry.css.value.calc.CalcNode.Product;
import dev.goldberry.css.value.calc.CalcNode.Round;
import dev.goldberry.css.value.calc.CalcNode.Rounding;
import dev.goldberry.css.value.calc.CalcNode.Sum;

/// The math functions as a stylesheet writes them, read into trees.
class CalcParserTest {

    static List<Token> tokens(String text) {
        return CssTokenizer.tokenize(text).stream()
                .filter(t -> !t.is(TokenType.EOF))
                .toList();
    }

    private static CalcNode parsed(String text) {
        var node = CalcParser.parse(tokens(text));
        assertTrue(node != null, () -> text + " should parse");
        return node;
    }

    @Test
    @DisplayName("a subtraction is a sum over a negated term")
    void subtraction() {
        assertEquals(
                new Sum(List.of(new Percentage(100), new Negate(new Dimension(8, "px")))), parsed("calc(100% - 8px)"));
    }

    @Test
    @DisplayName("multiplication binds tighter than addition, and a division is a product over an inverse")
    void precedence() {
        assertEquals(
                new Sum(List.of(
                        new Dimension(1, "em"),
                        new Product(List.of(new Plain(2), new Dimension(4, "px"))),
                        new Product(List.of(new Dimension(10, "px"), new Invert(new Plain(2)))))),
                parsed("calc(1em + 2 * 4px + 10px / 2)"));
    }

    @Test
    @DisplayName("parentheses group, and a nested math function is a value")
    void nesting() {
        assertEquals(
                new Product(List.of(new Sum(List.of(new Dimension(1, "px"), new Dimension(2, "px"))), new Plain(3))),
                parsed("calc((1px + 2px) * 3)"));
        assertEquals(
                new Sum(List.of(
                        new CalcNode.Min(List.of(new Dimension(1, "px"), new Dimension(2, "px"))),
                        new Dimension(3, "px"))),
                parsed("calc(min(1px, 2px) + 3px)"));
    }

    @Test
    @DisplayName("e and pi are numbers")
    void constants() {
        assertEquals(new Product(List.of(new Plain(Math.PI), new Dimension(2, "px"))), parsed("calc(pi * 2px)"));
        assertEquals(new Plain(Math.E), parsed("calc(e)"));
    }

    @Test
    @DisplayName("clamp() takes none for either bound")
    void clampWithNone() {
        assertEquals(new Clamp(null, new Dimension(5, "px"), new Dimension(9, "px")), parsed("clamp(none, 5px, 9px)"));
        assertEquals(new Clamp(new Dimension(1, "px"), new Dimension(5, "px"), null), parsed("clamp(1px, 5px, none)"));
    }

    @Test
    @DisplayName("round() takes a strategy first, and a step after the value")
    void roundStrategy() {
        assertEquals(
                new Round(Rounding.UP, new Dimension(5, "px"), new Dimension(2, "px")), parsed("round(up, 5px, 2px)"));
        assertEquals(new Round(Rounding.NEAREST, new Plain(2.5), null), parsed("round(2.5)"));
        assertEquals(new Round(Rounding.TO_ZERO, new Plain(-2.5), new Plain(1)), parsed("round(to-zero, -2.5, 1)"));
    }

    @Test
    @DisplayName("whitespace around the whole function is allowed, and a function name is read in any case")
    void whitespaceAndCase() {
        assertEquals(new Dimension(4, "px"), parsed("  CALC( 4px )  "));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "calc(1px -2px)",
                "calc(1px+2px)",
                "calc(1px +)",
                "calc()",
                "calc(1px, 2px)",
                "calc(1px",
                "calc(infinity)",
                "calc(nan * 1px)",
                "calc(none)",
                "min(none, 1px)",
                "clamp(1px, none, 2px)",
                "clamp(1px, 2px)",
                "round(up)",
                "round(1px, 2px, 3px)",
                "mod(1px)",
                "abs(1px, 2px)",
                "calc(1px) 2px",
                "var(--x)",
                "8px",
                "calc(foo)",
                "calc(1px * )",
                "calc(round(up, up, 1px))",
            })
    @DisplayName("refuses what is not a math expression")
    void refuses(String text) {
        assertNull(CalcParser.parse(tokens(text)), text);
    }

    @Test
    @DisplayName("names its functions, and only those")
    void functionNames() {
        for (var name : List.of("calc", "min", "max", "clamp", "round", "mod", "rem", "abs", "sign")) {
            assertTrue(CalcParser.reads(name), name);
        }
        assertFalse(CalcParser.reads("var"));
        assertFalse(CalcParser.reads("rgb"));
        assertTrue(CalcParser.starts(tokens("calc(1px)").getFirst()));
        assertFalse(CalcParser.starts(tokens("calc").getFirst()));
    }
}
