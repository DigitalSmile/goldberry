package dev.goldberry.css.cascade;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.TestElement;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.select.Selector;

/// Rules for one subtree: what they may hold, and where they rank in the cascade.
@DisplayName("ScopedRules")
class ScopedRulesTest {

    private static ScopedRules scoped(String css) {
        return ScopedRules.of(CssParser.parse(css));
    }

    private static String colour(StyleResolver resolver, TestElement element) {
        var value = resolver.resolve(element).get("color");
        return value == null ? "(none)" : value.getFirst().cssText();
    }

    private static StyleResolver window(String css) {
        return new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)));
    }

    @Nested
    @DisplayName("holds")
    class Holds {

        @Test
        @DisplayName("a class, an id, a type and the states input sets")
        void plainCompounds() {
            var rules = scoped(".a { color: red } #b { color: blue } text { color: green } .c:hover { color: gray }");

            assertEquals(4, rules.rules().size());
            assertTrue(ScopedRules.STATES.contains(Selector.PseudoClass.HOVER));
        }

        @ParameterizedTest(name = "refuses {0}")
        @ValueSource(
                strings = {
                    ".a .b { color: red }",
                    ".a > .b { color: red }",
                    ".a + .b { color: red }",
                    ".a:first-child { color: red }",
                    ".a:has(.b) { color: red }",
                    ".a:disabled { color: red }",
                    "* { color: red }",
                    ".a { --gb-text: red }",
                    ".a { color: red !important }",
                    ".a { color: revert }",
                    "@media (min-width: 10px) { .a { color: red } }",
                    "@starting-style { .a { color: red } }",
                })
        void refuses(String css) {
            assertThrows(IllegalArgumentException.class, () -> scoped(css));
        }

        @Test
        @DisplayName("nothing, as the one empty value")
        void none() {
            assertSame(ScopedRules.NONE, ScopedRules.of(List.of()));
            assertTrue(ScopedRules.NONE.isEmpty());
        }

        @Test
        @DisplayName("compared by value, so equal rules keep the styles under them cached")
        void byValue() {
            assertEquals(scoped(".a { color: red }"), scoped(".a { color: red }"));
            assertEquals(
                    scoped(".a { color: red }").hashCode(),
                    scoped(".a { color: red }").hashCode());
            assertNotEquals(scoped(".a { color: red }"), scoped(".a { color: blue }"));
        }

        @Test
        @DisplayName("each candidate once, in source order, found by class, id or type")
        void candidates() {
            var rules = scoped(".b { color: red } .a, .b { color: blue } #i { color: green } text { color: gray }");

            var found = rules.candidates("text", "i", Set.of("a", "b"));

            assertEquals(rules.rules(), found);
            assertEquals(List.of(), rules.candidates("row", null, Set.of("z")));
        }
    }

    @Nested
    @DisplayName("in the cascade")
    class Cascade {

        @Test
        @DisplayName("beats a more specific rule from the window's sheets")
        void beatsSpecificity() {
            var resolver = window(".page .x.y { color: red }");
            var leaf = element("text.x.y");
            element("column.page").scoped(scoped(".x { color: blue }")).with(leaf);

            assertEquals("blue", colour(resolver, leaf));
        }

        @Test
        @DisplayName("loses to an important declaration, which is how an application insists")
        void losesToImportant() {
            var resolver = window(".x { color: red !important }");
            var leaf = element("text.x");
            element("column").scoped(scoped(".x { color: blue }")).with(leaf);

            assertEquals("red", colour(resolver, leaf));
        }

        @Test
        @DisplayName("reaches only the subtree it is on")
        void staysInItsSubtree() {
            var resolver = window(".x { color: red }");
            var inside = element("text.x");
            var outside = element("text.x");
            element("column")
                    .with(element("column").scoped(scoped(".x { color: blue }")).with(inside), outside);

            assertEquals("blue", colour(resolver, inside));
            assertEquals("red", colour(resolver, outside));
        }

        @Test
        @DisplayName("lets the nearer of two scopes win")
        void nearerWins() {
            var resolver = window("");
            var leaf = element("text.x");
            element("column")
                    .scoped(scoped(".x.x { color: red }"))
                    .with(element("column").scoped(scoped(".x { color: blue }")).with(leaf));

            assertEquals("blue", colour(resolver, leaf));
        }

        @Test
        @DisplayName("orders its own rules by specificity, then source order")
        void ownOrder() {
            var resolver = window("");
            var first = element("text.x.y");
            var second = element("text.x");
            element("column")
                    .scoped(scoped(".x.y { color: red } .x { color: blue } .x { color: green }"))
                    .with(first, second);

            assertEquals("red", colour(resolver, first));
            assertEquals("green", colour(resolver, second));
        }

        @Test
        @DisplayName("applies a state rule while the node is in that state")
        void states() {
            var resolver = window("");
            var leaf = element("button.x");
            element("column")
                    .scoped(scoped(".x { color: blue } .x:hover { color: red }"))
                    .with(leaf);

            assertEquals("blue", colour(resolver, leaf));
            leaf.state(Selector.PseudoClass.HOVER, true);
            assertEquals("red", colour(resolver, leaf));
        }
    }
}
