package io.github.digitalsmile.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.Selector.Combinator;
import io.github.digitalsmile.goldberry.css.Selector.PseudoClass;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CssParserTest {

    private static StyleRule one(String css) {
        var rules = CssParser.parse(css);
        assertEquals(1, rules.size(), () -> "expected one rule, got " + rules);
        return rules.getFirst();
    }

    private static Selector selector(String css) {
        return one(css + " { color: red }").selectors().getFirst();
    }

    @Nested
    @DisplayName("selectors")
    class Selectors {

        @Test
        @DisplayName("a compound collects type, id, classes and pseudo-classes")
        void compound() {
            var key = selector("button#apply.primary.large:hover").key();

            assertEquals("button", key.type());
            assertEquals("apply", key.id());
            assertEquals(List.of("primary", "large"), key.classes());
            assertEquals(List.of(PseudoClass.HOVER), key.pseudoClasses());
        }

        @Test
        @DisplayName("a type selector is lowercased but a class name is not")
        void caseHandling() {
            // Element types are keywords; class names are the author's strings
            // and ".Primary" is not ".primary".
            var key = selector("BUTTON.Primary").key();
            assertEquals("button", key.type());
            assertEquals(List.of("Primary"), key.classes());
        }

        @Test
        @DisplayName("parts are stored rightmost first, each carrying its left-hand combinator")
        void combinatorOrder() {
            // ".a > .b .c" -- matching starts at .c and walks up, so .c is first.
            var parts = selector(".a > .b .c").parts();

            assertEquals(3, parts.size());
            assertEquals(List.of("c"), parts.get(0).compound().classes());
            assertEquals(Combinator.DESCENDANT, parts.get(0).combinator());
            assertEquals(List.of("b"), parts.get(1).compound().classes());
            assertEquals(Combinator.CHILD, parts.get(1).combinator());
            assertEquals(List.of("a"), parts.get(2).compound().classes());
            assertEquals(Combinator.NONE, parts.get(2).combinator());
        }

        @Test
        @DisplayName("a selector round-trips through toString in source order")
        void roundTrip() {
            // The cheapest check that the rightmost-first storage is not scrambled.
            assertEquals(".a > .b .c", selector(".a > .b .c").toString());
            assertEquals("button:hover", selector("button:hover").toString());
        }

        @Test
        @DisplayName("whitespace is a combinator only when a compound follows")
        void trailingWhitespaceIsNotACombinator() {
            // ".a   { ... }" is one compound, not ".a" descendant-of-nothing.
            assertEquals(1, selector(".a   ").parts().size());
        }

        @Test
        @DisplayName(".a .b and .a.b are different selectors")
        void descendantVersusCompound() {
            assertEquals(2, selector(".a .b").parts().size());
            assertEquals(1, selector(".a.b").parts().size());
            assertEquals(List.of("a", "b"), selector(".a.b").key().classes());
        }

        @Test
        @DisplayName("* is universal and constrains nothing")
        void universal() {
            assertTrue(selector("*").key().isUniversal());
            assertEquals(2, selector("* > .a").parts().size());
        }

        @Test
        @DisplayName("a selector list is one rule, not several")
        void selectorList() {
            var rule = one(".a, .b > c { color: red }");
            // One rule: they share declarations, and splitting them would give
            // the two halves different source orders.
            assertEquals(2, rule.selectors().size());
            assertEquals(1, rule.declarations().size());
        }
    }

    @Nested
    @DisplayName("specificity")
    class Specificity {

        @Test
        @DisplayName("ids beat classes beat types")
        void ordering() {
            var type = selector("button").specificity();
            var klass = selector(".primary").specificity();
            var id = selector("#apply").specificity();

            assertTrue(type < klass);
            assertTrue(klass < id);
        }

        @Test
        @DisplayName("a pseudo-class counts as a class")
        void pseudoClassesCountAsClasses() {
            assertEquals(selector(".a.b").specificity(), selector(".a:hover").specificity());
        }

        @Test
        @DisplayName("many classes never outrank one id")
        void noCarry() {
            // The packing is 10 bits per column, so this is really a test that
            // classes cannot overflow into the id column.
            var manyClasses = selector(".a.b.c.d.e.f.g.h.i.j").specificity();
            assertTrue(manyClasses < selector("#x").specificity());
        }

        @Test
        @DisplayName("specificity accumulates across combinators")
        void acrossCombinators() {
            assertTrue(selector(".a .b").specificity() > selector(".b").specificity());
        }
    }

    @Nested
    @DisplayName("declarations")
    class Declarations {

        @Test
        @DisplayName("a property is lowercased; a custom property keeps its case")
        void propertyCase() {
            assertEquals("color", one("a { COLOR: red }").declarations().getFirst().property());
            // "--gbAccent" and "--gbaccent" are different properties in CSS.
            assertEquals("--gbAccent", one("a { --gbAccent: red }").declarations().getFirst().property());
        }

        @Test
        @DisplayName("a custom property is flagged as one")
        void customProperties() {
            assertTrue(one("a { --gb-bg: #fff }").declarations().getFirst().isCustomProperty());
            assertFalse(one("a { color: red }").declarations().getFirst().isCustomProperty());
        }

        @Test
        @DisplayName("the value keeps its tokens, unparsed")
        void valueIsTokens() {
            // "4px 8px" means two lengths for padding and nothing for color; the
            // parser cannot know which, so it does not try.
            var value = one("a { padding: 4px 8px }").declarations().getFirst().value();
            var dimensions = value.stream().filter(t -> t.is(TokenType.DIMENSION)).toList();
            assertEquals(2, dimensions.size());
            assertEquals(4, dimensions.get(0).numeric(), 1e-9);
            assertEquals(8, dimensions.get(1).numeric(), 1e-9);
        }

        @Test
        @DisplayName("surrounding whitespace is trimmed from the value")
        void valueIsTrimmed() {
            var value = one("a { color:   red   }").declarations().getFirst().value();
            assertEquals(1, value.size());
            assertEquals("red", value.getFirst().text());
        }

        @Test
        @DisplayName("!important is recognised and removed from the value")
        void important() {
            var declaration = one("a { color: red !important }").declarations().getFirst();
            assertTrue(declaration.important());
            // The flag is not left in the value for a colour parser to trip on.
            assertEquals(1, declaration.value().size());
            assertEquals("red", declaration.value().getFirst().text());
        }

        @Test
        @DisplayName("a value may contain a function with its own parens and braces")
        void nestedFunction() {
            var value = one("a { color: var(--gb-accent) }").declarations().getFirst().value();
            assertTrue(value.stream().anyMatch(t -> t.is(TokenType.FUNCTION)));
            assertTrue(value.stream().anyMatch(t -> t.text().equals("--gb-accent")));
        }

        @Test
        @DisplayName("the last declaration needs no semicolon")
        void optionalTrailingSemicolon() {
            assertEquals(2, one("a { color: red; padding: 0 }").declarations().size());
            assertEquals(2, one("a { color: red; padding: 0; }").declarations().size());
        }

        @Test
        @DisplayName("an empty declaration is skipped rather than refused")
        void straySemicolons() {
            assertEquals(1, one("a { ; color: red ;; }").declarations().size());
        }

        @Test
        @DisplayName("declarations keep their source position for later errors")
        void positions() {
            var declaration = CssParser.parse("a {\n  color: red\n}").getFirst()
                    .declarations().getFirst();
            assertEquals(2, declaration.line());
            assertEquals(3, declaration.column());
        }
    }

    @Nested
    @DisplayName("rules and at-rules")
    class Rules {

        @Test
        @DisplayName("rules are numbered in source order")
        void sourceOrder() {
            var rules = CssParser.parse("a { color: red } b { color: blue }");
            assertEquals(0, rules.get(0).order());
            assertEquals(1, rules.get(1).order());
        }

        @Test
        @DisplayName("@media keeps the rules inside it")
        void mediaBlock() {
            // Nothing evaluates the condition yet. Dropping the block would make
            // a dark-mode stylesheet vanish silently, which is harder to
            // diagnose than one that applies too eagerly.
            var rules = CssParser.parse("@media (prefers-color-scheme: dark) { a { color: red } }");
            assertEquals(1, rules.size());
            assertEquals("a", rules.getFirst().selectors().getFirst().key().type());
        }

        @Test
        @DisplayName("comments and whitespace between rules are ignored")
        void betweenRules() {
            assertEquals(2, CssParser.parse("/* x */ a { color: red }\n\n/* y */ b { color: blue }").size());
        }

        @Test
        @DisplayName("an empty stylesheet parses to nothing")
        void empty() {
            assertTrue(CssParser.parse("").isEmpty());
            assertTrue(CssParser.parse("  /* nothing */  ").isEmpty());
        }
    }

    @Nested
    @DisplayName("errors")
    class Errors {

        @Test
        @DisplayName("an unknown pseudo-class is named rather than ignored")
        void unknownPseudoClass() {
            var thrown = assertThrows(CssSyntaxException.class, () -> CssParser.parse("a:hovered { color: red }"));
            // A rule that silently never matches is a bad afternoon; the message
            // lists what is supported.
            assertTrue(thrown.getMessage().contains(":hovered"));
            assertTrue(thrown.getMessage().contains(":hover"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "a[href] { color: red }",     // attribute selectors are not in the subset
                "a + b { color: red }",       // sibling combinators are not either
                "a::before { color: red }",   // nor pseudo-elements
                "@supports (x) { }",          // nor at-rules other than @media
        })
        @DisplayName("constructs outside the subset are refused, not dropped")
        void outsideTheSubset(String css) {
            assertThrows(CssSyntaxException.class, () -> CssParser.parse(css));
        }

        @Test
        @DisplayName("a numeric hash is not accepted as an id")
        void numericHashIsNotAnId() {
            assertThrows(CssSyntaxException.class, () -> CssParser.parse("#123456 { color: red }"));
        }

        @Test
        @DisplayName("an unclosed block is refused")
        void unclosedBlock() {
            assertThrows(CssSyntaxException.class, () -> CssParser.parse("a { color: red"));
        }

        @Test
        @DisplayName("a declaration with no value is refused")
        void emptyValue() {
            assertThrows(CssSyntaxException.class, () -> CssParser.parse("a { color: }"));
        }

        @Test
        @DisplayName("an error carries the line and column")
        void errorPosition() {
            var thrown = assertThrows(CssSyntaxException.class,
                    () -> CssParser.parse("a { color: red }\nb:nope { color: blue }"));
            assertEquals(2, thrown.line());
        }
    }

    @Nested
    @DisplayName("a realistic stylesheet")
    class Realistic {

        @Test
        @DisplayName("a themed component stylesheet parses whole")
        void themedComponent() {
            var rules = CssParser.parse("""
                    :root {
                      --gb-accent: #88c0d0;
                      --gb-bg: #2e3440;
                    }
                    button {
                      background: var(--gb-bg);
                      padding: 4px 8px;
                      border-radius: 3px;
                    }
                    button:hover, button:focus-visible {
                      background: var(--gb-accent);
                    }
                    .sidebar > button.primary:disabled {
                      opacity: 0.5 !important;
                    }
                    """);

            assertEquals(4, rules.size());

            var root = rules.get(0);
            assertEquals(2, root.declarations().size());
            assertTrue(root.declarations().stream().allMatch(Declaration::isCustomProperty));

            var states = rules.get(2);
            assertEquals(2, states.selectors().size());
            assertEquals(List.of(PseudoClass.FOCUS_VISIBLE),
                    states.selectors().get(1).key().pseudoClasses());

            var nested = rules.get(3);
            var parts = nested.selectors().getFirst().parts();
            assertEquals(2, parts.size());
            assertEquals("button", parts.get(0).compound().type());
            assertEquals(Combinator.CHILD, parts.get(0).combinator());
            assertTrue(nested.declarations().getFirst().important());
        }
    }
}
