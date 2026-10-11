package dev.goldberry.css.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.StyleRule;

/// `@supports`, answered against the engine when the sheet is parsed.
class SupportsTest {

    private static List<StyleRule> rules(String css) {
        return CssParser.parseSheet(css, ParseMode.STRICT, "test").rules();
    }

    private static boolean applies(String condition) {
        return rules("@supports " + condition + " { a { color: red } }").size() == 1;
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "(display: flex)                         | true",
                "(display: grid)                         | false",
                "(aspect-ratio: 16 / 9)                  | true",
                "(width: calc(100% - 8px))               | false",
                "(width: 50vw)                           | true",
                "(float: left)                           | false",
                "(--anything: at all)                    | true",
                "not (display: grid)                     | true",
                "(color: red) and (z-index: 2)           | true",
                "(color: red) and (display: grid)        | false",
                "(display: grid) or (display: contents)  | true",
                "((display: grid) or (color: red))       | true",
                "not ((display: flex))                   | false",
                "selector(a:is(b, c) > d)                | true",
                "selector(a + b ~ c:has(> d))            | true",
                "selector(a[x])                          | false",
                "selector(a::before)                     | false",
                "font-tech(color-COLRv1)                 | false",
                "(color)                                 | false",
            })
    @DisplayName("holds as the engine answers")
    void conditions(String condition, boolean expected) {
        assertEquals(expected, applies(condition), condition);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"(color: red) and (z-index: 1) or (opacity: 1)", "color: red", "(color: red", ""})
    @DisplayName("refuses what is not a condition")
    void malformed(String condition) {
        assertThrows(CssSyntaxException.class, () -> rules("@supports " + condition + " { a { color: red } }"));
    }

    @Test
    @DisplayName("a block that does not apply is skipped whole, and nothing is reported dropped")
    void skipped() {
        var parsed = CssParser.parseSheet(
                "@supports (display: grid) { a { color: red } @media (min-width: 1px) { b { color: red } } } c { }",
                ParseMode.LENIENT,
                "test");
        assertEquals(1, parsed.rules().size());
        assertTrue(parsed.dropped().isEmpty());
    }

    @Test
    @DisplayName("holds @media and nests in a rule")
    void nesting() {
        var parsed = rules("""
                @supports (display: flex) { @media (min-width: 1px) { a { color: red } } }
                b { color: red; @supports (z-index: 1) { padding: 1px } @supports (display: grid) { margin: 1px } }
                """);
        assertEquals(3, parsed.size());
        assertEquals("b", parsed.get(2).selectors().getFirst().toString());
        assertEquals("padding", parsed.get(2).declarations().getFirst().property());
    }
}
