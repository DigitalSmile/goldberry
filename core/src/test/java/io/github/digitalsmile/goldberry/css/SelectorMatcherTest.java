package io.github.digitalsmile.goldberry.css;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SelectorMatcherTest {

    private static Selector selector(String css) {
        return CssParser.parse(css + " { color: red }").getFirst().selectors().getFirst();
    }

    private static boolean matches(String css, StyleElement element) {
        return SelectorMatcher.matches(selector(css), element);
    }

    @Nested
    @DisplayName("a single compound")
    class Compound {

        @Test
        @DisplayName("type, id and class all have to hold")
        void everyPartMustHold() {
            var button = element("button.primary#apply");

            assertTrue(matches("button", button));
            assertTrue(matches(".primary", button));
            assertTrue(matches("#apply", button));
            assertTrue(matches("button.primary#apply", button));

            assertFalse(matches("input", button));
            assertFalse(matches(".secondary", button));
            assertFalse(matches("#cancel", button));
            assertFalse(matches("button.secondary", button));
        }

        @Test
        @DisplayName("* matches anything")
        void universal() {
            assertTrue(matches("*", element("button")));
        }

        @Test
        @DisplayName("all of a compound's classes must be present, not just one")
        void everyClass() {
            var button = element("button.a.b");
            assertTrue(matches(".a.b", button));
            assertFalse(matches(".a.c", button));
        }

        @Test
        @DisplayName("a state pseudo-class is asked of the element")
        void states() {
            var hovered = element("button:hover");
            assertTrue(matches("button:hover", hovered));
            assertFalse(matches("button:disabled", hovered));
            assertFalse(matches("button:hover", element("button")));
        }
    }

    @Nested
    @DisplayName(":root")
    class Root {

        @Test
        @DisplayName("matches the element with no parent, and only that one")
        void onlyTheRoot() {
            var root = element("window");
            var child = element("button");
            root.with(child);

            // Answered by the tree rather than by the element, so an element
            // cannot claim to be root inside somebody else's subtree.
            assertTrue(matches(":root", root));
            assertFalse(matches(":root", child));
        }
    }

    @Nested
    @DisplayName("combinators")
    class Combinators {

        @Test
        @DisplayName("child matches only a direct parent")
        void child() {
            var root = element("window").with(element("row").with(element("button")));
            var button = root.descend(2);

            assertTrue(matches("row > button", button));
            assertFalse(matches("window > button", button));
        }

        @Test
        @DisplayName("descendant matches at any depth")
        void descendant() {
            var root = element("window").with(element("row").with(element("button")));
            var button = root.descend(2);

            assertTrue(matches("window button", button));
            assertTrue(matches("row button", button));
            assertFalse(matches("form button", button));
        }

        @Test
        @DisplayName("a chain of three is checked all the way up")
        void threeDeep() {
            var root = element("window.app").with(element("row.sidebar").with(element("button.primary")));
            var button = root.descend(2);

            assertTrue(matches(".app .sidebar > .primary", button));
            assertFalse(matches(".app > .sidebar > .other", button));
        }

        @Test
        @DisplayName("a descendant match backtracks when the first candidate ancestor fails")
        void backtracking() {
            // .a > .b > .b > .c  against  ".a > .b .c"
            //
            // Walking up greedily from .c finds the INNER .b first, whose parent
            // is .b and not .a, so a matcher without backtracking reports no
            // match. The outer .b does satisfy it.
            var root = element("div.a")
                    .with(element("div.b")
                            .with(element("div.b")
                                    .with(element("div.c"))));
            var leaf = root.descend(3);

            assertTrue(matches(".a > .b .c", leaf));
        }

        @Test
        @DisplayName("backtracking does not invent a match that is not there")
        void backtrackingIsNotPermissive() {
            var root = element("div.a").with(element("div.b").with(element("div.c")));
            var leaf = root.descend(2);

            // .c's parent is .b, whose parent is .a -- there is no .z anywhere.
            assertFalse(matches(".z > .b .c", leaf));
        }

        @Test
        @DisplayName("a selector longer than the tree is deep does not match")
        void runsOutOfAncestors() {
            var root = element("window").with(element("button"));
            assertFalse(matches("a b c d", root.descend(1)));
        }
    }
}
