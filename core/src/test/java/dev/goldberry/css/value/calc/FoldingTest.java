package dev.goldberry.css.value.calc;

import static dev.goldberry.css.value.calc.CalcParserTest.tokens;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.value.CssLength;

/// The fold table: what each expression comes to, and what does not fold.
class FoldingTest {

    /// `em` is 10px, `rem` 16px, and the window 1000 by 500.
    private static final CssLength.Context CONTEXT = new CssLength.Context(10, 16).withViewport(1000, 500);

    private static @Nullable Quantity fold(String text) {
        var tree = CalcParser.parse(tokens(text));
        assertNotNull(tree, () -> text + " should parse");
        return Folding.fold(tree, ContextUnits.of(CONTEXT));
    }

    @ParameterizedTest(name = "{0} = {2}px + {3}%")
    @CsvSource(
            delimiter = '|',
            value = {
                "calc(2px + 3px)                  | LENGTH | 5    | 0",
                "calc(1em + 2rem)                 | LENGTH | 42   | 0",
                "calc(100% - 8px)                 | LENGTH | -8   | 100",
                "calc(50% + 10%)                  | LENGTH | 0    | 60",
                "calc(2 * (1px + 1em) / 4)        | LENGTH | 5.5  | 0",
                "calc(10vw - 5vh)                 | LENGTH | 75   | 0",
                "calc(1in - 72pt)                 | LENGTH | 0    | 0",
                "calc(-1 * (100% - 2px))          | LENGTH | 2    | -100",
                "min(10px, 2em, 30px)             | LENGTH | 10   | 0",
                "max(10px, 2em, 15px)             | LENGTH | 20   | 0",
                "min(20%, 30%)                    | LENGTH | 0    | 20",
                "clamp(4px, 1em, 12px)            | LENGTH | 10   | 0",
                "clamp(4px, 1px, 12px)            | LENGTH | 4    | 0",
                "clamp(4px, 99px, 12px)           | LENGTH | 12   | 0",
                "clamp(20px, 5px, 10px)           | LENGTH | 20   | 0",
                "clamp(none, 99px, 12px)          | LENGTH | 12   | 0",
                "clamp(4px, 1px, none)            | LENGTH | 4    | 0",
                "round(17px, 5px)                 | LENGTH | 15   | 0",
                "round(up, 16px, 5px)             | LENGTH | 20   | 0",
                "round(down, 19px, 5px)           | LENGTH | 15   | 0",
                "round(to-zero, -19px, 5px)       | LENGTH | -15  | 0",
                "round(down, -16px, 5px)          | LENGTH | -20  | 0",
                "round(12.5px, 5px)               | LENGTH | 15   | 0",
                "mod(7px, 3px)                    | LENGTH | 1    | 0",
                "mod(-7px, 3px)                   | LENGTH | 2    | 0",
                "rem(-7px, 3px)                   | LENGTH | -1   | 0",
                "abs(-4px)                        | LENGTH | 4    | 0",
                "calc(2 * 3)                      | NUMBER | 6    | 0",
                "sign(-4px)                       | NUMBER | -1   | 0",
                "round(2.5)                       | NUMBER | 3    | 0",
                "calc(90deg + 0.25turn)           | ANGLE  | 180  | 0",
                "calc(1s - 200ms)                 | TIME   | 800  | 0",
            })
    @DisplayName("folds")
    void folds(String text, Quantity.Kind kind, double value, double percent) {
        var folded = fold(text);
        assertNotNull(folded, text);
        assertEquals(kind, folded.kind(), text);
        assertEquals(value, folded.value(), 1e-9, text);
        assertEquals(percent, folded.percent(), 1e-9, text);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "calc(1px + 2)",
                "calc(1px + 1s)",
                "calc(2px * 3px)",
                "calc(4px / 0)",
                "calc(4px / 2px)",
                "calc(4 / (1 - 1))",
                "min(100px, 50%)",
                "max(1px, 2)",
                "clamp(1px, 50%, 10px)",
                "mod(1px, 0px)",
                "rem(1px, 0px)",
                "round(1px, 0px)",
                "round(1px)",
                "calc(1px + 1foo)",
            })
    @DisplayName("does not fold a mismatch, a division by zero, or a comparison a percentage would need a box for")
    void doesNotFold(String text) {
        assertNull(fold(text), text);
    }

    @Test
    @DisplayName("a viewport unit does not fold before a window says how big it is")
    void viewportUnknown() {
        var tree = CalcParser.parse(tokens("calc(10vw + 1px)"));
        assertNotNull(tree);
        assertNull(Folding.fold(tree, ContextUnits.of(new CssLength.Context(10, 16))));
    }

    @Test
    @DisplayName("only a length carries a percentage")
    void percentOnlyOnLengths() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(Quantity.Kind.NUMBER, 1, 5));
    }
}
