package dev.goldberry.css.parse;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.StyleRule;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.media.MediaCondition;

/// CSS nesting, flattened by the parser into ordinary rules.
class NestingTest {

    private static List<StyleRule> rules(String css) {
        return CssParser.parseSheet(css, ParseMode.STRICT, "test").rules();
    }

    /// Each rule as `selector, … { property … }`.
    private static List<String> written(String css) {
        return rules(css).stream()
                .map(rule -> String.join(
                                ", ",
                                rule.selectors().stream().map(Object::toString).toList())
                        + " {"
                        + String.join(
                                "",
                                rule.declarations().stream()
                                        .map(d -> " " + d.property())
                                        .toList())
                        + " }")
                .toList();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "a { &:hover { color: red } }             | a:hover { color }",
                "a { b { color: red } }                   | a b { color }",
                "a { > b { color: red } }                 | a > b { color }",
                "a { .x & { color: red } }                | .x a { color }",
                "a b { &.on { color: red } }              | a b.on { color }",
                "a > b { & c { color: red } }             | a > b c { color }",
                ".x { div& { color: red } }               | div.x { color }",
                "a { b& { color: red } }                  | b:is(a) { color }",
                "a, b { & c { color: red } }              | :is(a, b) c { color }",
                "a, b { :hover { color: red } }           | :is(a, b) :hover { color }",
                "a { b { c { color: red } } }             | a b c { color }",
                "a { b, > c { color: red } }              | a b, a > c { color }",
                "a { :is(&, x) { color: red } }           | :is(:is(a), x) { color }",
            })
    @DisplayName("joins a nested selector to its parent")
    void joins(String css, String expected) {
        assertEquals(
                List.of(expected),
                written(css).subList(written(css).size() - 1, written(css).size()),
                css);
    }

    @Test
    @DisplayName("keeps source order: the rule's own declarations, the nested rule, then what follows it")
    void order() {
        var parsed = rules("a { color: red; & b { color: blue } padding: 1px }");
        assertEquals(
                List.of("a { color }", "a b { color }", "a { padding }"),
                written("a { color: red; & b { color: blue } padding: 1px }"));
        assertTrue(parsed.get(0).order() < parsed.get(1).order());
        assertTrue(parsed.get(1).order() < parsed.get(2).order());
    }

    @Test
    @DisplayName("a rule that holds only nested rules is not a rule of its own, and an empty one still is")
    void emptyParents() {
        assertEquals(List.of("a b { color }"), written("a { b { color: red } }"));
        assertEquals(List.of("a { }"), written("a { }"));
    }

    @Test
    @DisplayName("@media inside a rule applies the rule's selector under its condition")
    void nestedMedia() {
        var parsed = rules("a { color: red; @media (min-width: 600px) { color: blue; b { padding: 1px } } }");
        assertEquals(3, parsed.size());
        assertEquals(MediaCondition.ALWAYS, parsed.get(0).media());
        assertNotEquals(MediaCondition.ALWAYS, parsed.get(1).media());
        assertEquals("a", parsed.get(1).selectors().getFirst().toString());
        assertEquals("a b", parsed.get(2).selectors().getFirst().toString());
        assertEquals(parsed.get(1).media(), parsed.get(2).media());
    }

    @Test
    @DisplayName("the parent is as specific as :is() of its list")
    void specificity() {
        var selector =
                rules("a, #b { & c { color: red } }").getFirst().selectors().getFirst();
        assertEquals((1 << 20) | 1, selector.specificity());
    }

    @Nested
    @DisplayName("what it refuses")
    class Refusals {

        @Test
        @DisplayName("a lenient sheet drops a nested rule outside the subset and keeps the rest of its block")
        void dropsOnlyTheNestedRule() {
            var parsed = CssParser.parseSheet(
                    "a { color: red; b::before { color: blue } padding: 1px } d { color: red }",
                    ParseMode.LENIENT,
                    "t");
            assertEquals(1, parsed.dropped().size());
            assertEquals(
                    List.of("a", "a", "d"),
                    parsed.rules().stream()
                            .map(rule -> rule.selectors().getFirst().toString())
                            .toList());
        }

        @Test
        @DisplayName("a strict sheet refuses it as outside the subset")
        void strict() {
            var thrown = assertThrows(CssSyntaxException.class, () -> rules("a { b::before { color: blue } }"));
            assertTrue(thrown.isUnsupportedFeature());
        }

        @Test
        @DisplayName(
                "an at-rule other than @media and @supports inside a rule, and & outside one, are outside the subset")
        void atRulesAndTopLevelNesting() {
            assertTrue(assertThrows(CssSyntaxException.class, () -> rules("a { @scope (.b) { color: red } }"))
                    .isUnsupportedFeature());
            assertTrue(assertThrows(CssSyntaxException.class, () -> rules("& { color: red }"))
                    .isUnsupportedFeature());
        }

        @Test
        @DisplayName("a keyframe still holds declarations only")
        void keyframes() {
            assertThrows(CssSyntaxException.class, () -> rules("@keyframes k { from { a { color: red } } }"));
        }
    }

    @Test
    @DisplayName("a flattened rule matches what the nested one says")
    void matches() {
        var resolver = new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                card {
                  padding: 1px;
                  > text { color: red }
                  &.raised text { opacity: 0.5 }
                }
                """)));
        var text = element("text");
        element("card.raised").with(text);
        var resolved = resolver.resolve(text);
        assertEquals("red", resolved.get("color").getFirst().text());
        assertTrue(resolved.containsKey("opacity"));
    }
}
