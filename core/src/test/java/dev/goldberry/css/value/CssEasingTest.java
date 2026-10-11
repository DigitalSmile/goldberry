package dev.goldberry.css.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.motion.Easing;

/// CSS's timing functions as a stylesheet writes them.
class CssEasingTest {

    private static List<Token> tokens(String text) {
        return CssTokenizer.tokenize(text).stream()
                .filter(t -> !t.is(TokenType.EOF))
                .toList();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "ease-enter                          | ease-enter",
                "LINEAR                              | linear",
                "ease                                | cubic-bezier(0.25, 0.1, 0.25, 1)",
                "cubic-bezier(0.1, 0.7, 1.0, 0.1)    | cubic-bezier(0.1, 0.7, 1, 0.1)",
                "cubic-bezier(0, -2, 1, 3)           | cubic-bezier(0, -2, 1, 3)",
                "steps(4)                            | steps(4, jump-end)",
                "steps(4, start)                     | steps(4, jump-start)",
                "steps(2, jump-none)                 | steps(2, jump-none)",
                "steps(3, jump-both)                 | steps(3, jump-both)",
                "step-end                            | steps(1, jump-end)",
                "linear(0, 1)                        | linear",
                "linear(0, 0.25 75%, 1)              | linear(0 0%, 0.25 75%, 1 100%)",
                "linear(0, 0.5, 1)                   | linear(0 0%, 0.5 50%, 1 100%)",
                "linear(0, 0.5 25% 75%, 1)           | linear(0 0%, 0.5 25%, 0.5 75%, 1 100%)",
                "linear(0 20%, 0.5 10%, 1)           | linear(0 20%, 0.5 20%, 1 100%)",
                "linear(0, 0.2, 0.4, 1 60%)          | linear(0 0%, 0.2 20%, 0.4 40%, 1 60%)",
            })
    @DisplayName("reads each kind, and writes it back as CSS")
    void reads(String written, String read) {
        var curve = CssEasing.parse(tokens(written));
        assertEquals(read, curve == null ? null : curve.cssName(), written);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "cubic-bezier(1.1, 0, 0, 1)",
                "cubic-bezier(0, 0, 1)",
                "cubic-bezier(0, 0, 1, 1, 1)",
                "steps(0)",
                "steps(1, jump-none)",
                "steps(2.5)",
                "steps(2, sideways)",
                "linear(1)",
                "linear(0, , 1)",
                "linear(50%)",
                "ease-wobble",
                "bounce(1)",
            })
    @DisplayName("refuses what CSS calls invalid")
    void refuses(String written) {
        assertNull(CssEasing.parse(tokens(written)), written);
    }

    @Test
    @DisplayName("tells the design system's curves from CSS's, and a curve from anything else")
    void kinds() {
        assertTrue(CssEasing.isSystemCurve(tokens("ease-exit")));
        assertFalse(CssEasing.isSystemCurve(tokens("ease")));
        assertFalse(CssEasing.isSystemCurve(tokens("steps(2)")));
        assertTrue(CssEasing.looksLikeEasing(tokens("steps(0)")), "a curve, if not a readable one");
        assertTrue(CssEasing.looksLikeEasing(tokens("ease-wobble")));
        assertFalse(CssEasing.looksLikeEasing(tokens("opacity")));
        assertFalse(CssEasing.looksLikeEasing(tokens("200ms")));
    }

    @Test
    @DisplayName("an overshooting curve overshoots")
    void overshoot() {
        var curve = CssEasing.parse(tokens("linear(0, 1.2 50%, 1)"));
        assertEquals(1.2, curve.at(0.5), 1e-12);
        assertEquals(Easing.LINEAR, CssEasing.parse(tokens("linear")));
    }
}
