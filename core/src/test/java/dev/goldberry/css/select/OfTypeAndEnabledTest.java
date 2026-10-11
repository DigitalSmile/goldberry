package dev.goldberry.css.select;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.TestElement;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.parse.CssParser;

/// `:enabled`, and the structural pseudo-classes counted among siblings of one
/// type: `:first-of-type`, `:last-of-type`, `:only-of-type`, `:nth-of-type()`
/// and `:nth-last-of-type()`.
class OfTypeAndEnabledTest {

    private static Selector selector(String css) {
        return CssParser.parse(css + " { color: red }").getFirst().selectors().getFirst();
    }

    /// `row( a b a c a )`: three `a`s among five children.
    private static List<TestElement> children() {
        var row = element("row");
        var kids = List.of(element("a"), element("b"), element("a"), element("c"), element("a"));
        row.with(kids.toArray(TestElement[]::new));
        return kids;
    }

    /// The 1-based positions among the five children that `css` matches.
    private static List<Integer> matching(String css) {
        var selector = selector(css);
        var kids = children();
        var matched = new ArrayList<Integer>();
        for (var i = 0; i < kids.size(); i++) {
            if (SelectorMatcher.matches(selector, kids.get(i))) {
                matched.add(i + 1);
            }
        }
        return matched;
    }

    @Nested
    @DisplayName("by type")
    class ByType {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "a:first-of-type        | [1]",
                    "a:last-of-type         | [5]",
                    "a:nth-of-type(2)       | [3]",
                    "a:nth-of-type(odd)     | [1, 5]",
                    "a:nth-last-of-type(2)  | [3]",
                    ":first-of-type         | [1, 2, 4]",
                    ":only-of-type          | [2, 4]",
                    "a:first-child          | [1]",
                    "c:first-of-type        | [4]",
                })
        @DisplayName("counts among the siblings of the element's own type")
        void positions(String css, String expected) {
            assertEquals(expected, matching(css).toString());
        }

        @Test
        @DisplayName("writes itself back as CSS, and counts as a pseudo-class")
        void names() {
            var key = selector("a:nth-last-of-type(2n+1)").key();
            assertEquals("nth-last-of-type(2n+1)", key.structural().getFirst().cssName());
            assertEquals(
                    selector("a:hover").specificity(),
                    selector("a:first-of-type").specificity());
        }

        @Test
        @DisplayName("refuses to count by type twice")
        void nested() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Structural.OfType(new Structural.OfType(Structural.firstChild())));
        }

        @Test
        @DisplayName("a position change by type alone is one a rule can see")
        void invalidation() {
            var resolver = new StyleResolver(
                    List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "a:first-of-type { color: red }")));
            // An `a` that stays third of five but stops being the first `a`.
            var before = new SiblingPosition(2, 5, 0, 2);
            var after = new SiblingPosition(2, 5, 1, 2);
            assertTrue(resolver.positionMatters(before, after));
            assertFalse(resolver.positionMatters(before, new SiblingPosition(3, 6, 0, 2)));
        }
    }

    @Nested
    @DisplayName(":enabled")
    class Enabled {

        @Test
        @DisplayName("holds exactly when :disabled does not")
        void negation() {
            var enabled = selector("button:enabled");
            assertTrue(SelectorMatcher.matches(enabled, element("button")));
            assertFalse(SelectorMatcher.matches(enabled, element("button:disabled")));
        }

        @Test
        @DisplayName("on an ancestor, reaches below through a change to :disabled")
        void reach() {
            var resolver = new StyleResolver(
                    List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "form:enabled text { color: red }")));
            assertTrue(resolver.reachesDescendants(Selector.PseudoClass.DISABLED, "form"));
        }
    }
}
