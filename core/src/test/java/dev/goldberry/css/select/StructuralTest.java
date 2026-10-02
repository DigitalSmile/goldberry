package dev.goldberry.css.select;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.TestElement;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;

/// `:first-child`, `:last-child`, `:only-child`, `:nth-child()` and
/// `:nth-last-child()`: parsed, matched against a position, and counted in
/// specificity as a pseudo-class.
class StructuralTest {

    private static Selector selector(String css) {
        return CssParser.parse(css + " { color: red }").getFirst().selectors().getFirst();
    }

    /// The 1-based positions out of `count` that `css` matches.
    private static List<Integer> positions(String css, int count) {
        var structural = selector(css).key().structural().getFirst();
        return IntStream.range(0, count)
                .filter(index -> structural.matches(index, count))
                .map(index -> index + 1)
                .boxed()
                .toList();
    }

    @Nested
    @DisplayName("positions")
    class Positions {

        @ParameterizedTest(name = "{0} of 7 is {1}")
        @CsvSource(
                delimiter = '|',
                value = {
                    ":first-child          | [1]",
                    ":last-child           | [7]",
                    ":nth-child(odd)       | [1, 3, 5, 7]",
                    ":nth-child(even)      | [2, 4, 6]",
                    ":nth-child(3)         | [3]",
                    ":nth-child(3n)        | [3, 6]",
                    ":nth-child(3n+1)      | [1, 4, 7]",
                    ":nth-child(2n - 1)    | [1, 3, 5, 7]",
                    ":nth-child(n+5)       | [5, 6, 7]",
                    ":nth-child(-n+2)      | [1, 2]",
                    ":nth-last-child(2)    | [6]",
                    ":nth-last-child(-n+3) | [5, 6, 7]",
                })
        @DisplayName("match as CSS counts them, from 1")
        void match(String css, String expected) {
            assertEquals(expected, positions("a" + css, 7).toString());
        }

        @Test
        @DisplayName(":only-child holds for one child and no more")
        void onlyChild() {
            var only = selector("a:only-child").key().structural().getFirst();
            assertTrue(only.matches(0, 1));
            assertFalse(only.matches(0, 2));
        }

        @Test
        @DisplayName("a formula that is not An+B is a mistake, not a feature the subset lacks")
        void badFormula() {
            var thrown = assertThrows(CssSyntaxException.class, () -> CssParser.parse("a:nth-child(2n1) { }"));
            assertFalse(thrown.isUnsupportedFeature());
        }
    }

    @Nested
    @DisplayName("in a selector")
    class InASelector {

        @Test
        @DisplayName("prints as written, and counts in specificity as a pseudo-class")
        void roundTripAndSpecificity() {
            assertEquals(
                    "row > item:first-child", selector("row > item:first-child").toString());
            assertEquals(
                    "item:nth-child(2n+1)", selector("item:nth-child(2n+1)").toString());
            assertEquals(
                    "item:nth-last-child(3)", selector("item:nth-last-child(3)").toString());
            assertEquals(
                    selector("item.a").specificity(),
                    selector("item:last-child").specificity());
        }

        @Test
        @DisplayName("matches against where the element sits among its parent's children")
        void matchesTheTree() {
            var first = element("item");
            var middle = element("item");
            var last = element("item");
            TestElement.element("row").with(first, middle, last);

            var firstChild = selector("row > :first-child");
            var lastChild = selector("item:last-child");
            assertTrue(SelectorMatcher.matches(firstChild, first));
            assertFalse(SelectorMatcher.matches(firstChild, middle));
            assertTrue(SelectorMatcher.matches(lastChild, last));
            assertFalse(SelectorMatcher.matches(lastChild, first));
            assertTrue(SelectorMatcher.matches(selector("item:nth-child(even)"), middle));
        }

        @Test
        @DisplayName("a root is the first, last and only child of nothing")
        void root() {
            var root = element("window");
            assertTrue(SelectorMatcher.matches(selector("window:only-child"), root));
        }
    }
}
