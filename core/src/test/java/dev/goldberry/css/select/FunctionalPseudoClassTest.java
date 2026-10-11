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
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.TestElement;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;

/// `:is()`, `:where()`, `:not()` and `:nth-child(An+B of S)`.
class FunctionalPseudoClassTest {

    private static Selector selector(String css) {
        return CssParser.parse(css + " { color: red }").getFirst().selectors().getFirst();
    }

    /// `list( a.on b a.on:hover c.on a )` under a `card`.
    private static List<TestElement> children() {
        var card = element("card");
        var list = element("list");
        card.with(list);
        var kids = List.of(element("a.on"), element("b"), element("a.on:hover"), element("c.on"), element("a"));
        list.with(kids.toArray(TestElement[]::new));
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

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                ":is(a, c)                     | [1, 3, 4, 5]",
                ":where(.on)                   | [1, 3, 4]",
                "a:not(.on)                    | [5]",
                ":not(a, b)                    | [4]",
                ":not(:hover)                  | [1, 2, 4, 5]",
                ":is(list > a)                 | [1, 3, 5]",
                ":is(card a)                   | [1, 3, 5]",
                ":is(table a)                  | []",
                "a:is(.on):not(:hover)         | [1]",
                ":not(:is(a, b))               | [4]",
                ":nth-child(2 of .on)          | [3]",
                ":nth-child(odd of .on)        | [1, 4]",
                ":nth-last-child(1 of .on)     | [4]",
                ":nth-child(1 of a, c)         | [1]",
                "a:nth-child(2n of a)          | [3]",
                ":is(a, b):nth-child(odd)      | [1, 3, 5]",
            })
    @DisplayName("matches")
    void matches(String css, String expected) {
        assertEquals(expected, matching(css).toString(), css);
    }

    @Test
    @DisplayName("writes itself back as CSS")
    void writesBack() {
        assertEquals("a:not(.b, c > d)", selector("a:not(.b, c > d)").toString());
        // Simple selectors print in a fixed order, which is the same compound.
        assertEquals(":hover:is(a, b)", selector(":is(a, b):hover").toString());
        assertEquals(":where(.x)", selector(":where(.x)").toString());
        assertEquals(
                ":nth-child(2n+1 of .on)", selector(":nth-child(2n+1 of .on)").toString());
        assertEquals(
                ":nth-last-child(1 of a)", selector(":nth-last-child(1 of a)").toString());
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                // (ids, classes, types)
                ":is(#a, .b)              | 1,0,0",
                ":where(#a, .b)           | 0,0,0",
                "a:not(.b)                | 0,1,1",
                "a:not(b, .c)             | 0,1,1",
                ":nth-child(1 of .x)      | 0,2,0",
                ":where(.card) text       | 0,0,1",
                ":is(a b, c) d            | 0,0,3",
            })
    @DisplayName("is as specific as CSS says")
    void specificity(String css, String expected) {
        var packed = selector(css).specificity();
        assertEquals(expected, (packed >>> 20) + "," + ((packed >>> 10) & 1023) + "," + (packed & 1023), css);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {":is() { color: red }", "a:nth-of-type(1 of .b) { color: red }", ":not(a { color: red }"})
    @DisplayName("refuses an empty list, a filter on a count by type, and an unclosed list")
    void malformed(String css) {
        assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet(css, ParseMode.STRICT, "test"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {":is(a[x]) { color: red }", ":not(::before) { color: red }", "a:has(b:has(c)) { color: red }"})
    @DisplayName("an argument outside the subset drops the rule as unsupported")
    void unsupported(String css) {
        var thrown = assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet(css, ParseMode.STRICT, "t"));
        assertTrue(thrown.isUnsupportedFeature(), thrown::getMessage);
        assertEquals(
                1,
                CssParser.parseSheet(css + " b { color: blue }", ParseMode.LENIENT, "t")
                        .rules()
                        .size());
    }

    @Nested
    @DisplayName("in the cascade")
    class Cascade {

        private static StyleResolver resolver(String css) {
            return new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)));
        }

        @Test
        @DisplayName(":where() loses to anything, and :is() is as strong as its strongest argument")
        void strength() {
            var text = element("text");
            element("card").with(text);
            var resolved = resolver("""
                    :where(card) text { color: red }
                    text { color: blue }
                    :is(card, #x) text { padding: 1px }
                    card text { padding: 2px }
                    """).resolve(text);
            assertEquals("blue", resolved.get("color").getFirst().text());
            assertEquals("1", resolved.get("padding").getFirst().text());
        }

        @Test
        @DisplayName("a rule whose subject is :is(a, b) reaches both types and no other")
        void bucketedByArgument() {
            var resolver = resolver(":is(button, toggle) { color: red }");
            assertTrue(resolver.resolve(element("button")).containsKey("color"));
            assertTrue(resolver.resolve(element("toggle")).containsKey("color"));
            assertFalse(resolver.resolve(element("text")).containsKey("color"));
        }

        @Test
        @DisplayName("a state inside an argument in an ancestor position reaches below")
        void ancestorStates() {
            var resolver =
                    resolver(":is(row:hover, cell:focus) text { color: red } x:not(menu:active y) { color: red }");
            assertTrue(resolver.reachesDescendants(Selector.PseudoClass.HOVER, "row"));
            assertTrue(resolver.reachesDescendants(Selector.PseudoClass.FOCUS, "cell"));
            assertTrue(resolver.reachesDescendants(Selector.PseudoClass.ACTIVE, "menu"));
            assertFalse(resolver.reachesDescendants(Selector.PseudoClass.HOVER, "text"));
        }

        @Test
        @DisplayName("a filtered count makes siblings matter, and a plain one does not")
        void siblingsMatter() {
            assertTrue(resolver("a:nth-child(2 of .on) { color: red }").siblingsMatter());
            assertTrue(resolver(":is(a:nth-child(2 of .on)) { color: red }").siblingsMatter());
            assertFalse(resolver("a:nth-child(2) { color: red }").siblingsMatter());
            assertTrue(resolver("a:nth-child(2 of .on) { color: red }")
                    .positionMatters(SiblingPosition.ALONE, SiblingPosition.ALONE));
        }

        @Test
        @DisplayName("a position inside an argument is indexed like one outside it")
        void positionsInside() {
            var resolver = resolver(":is(row:first-child) text { color: red }");
            assertTrue(resolver.positionReachesDescendants("row"));
            assertTrue(resolver.positionMatters(new SiblingPosition(0, 2, 0, 2), new SiblingPosition(1, 2, 1, 2)));
        }
    }
}
