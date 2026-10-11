package dev.goldberry.css.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;

/// An application's sheet is lenient and loud, the toolkit's own is strict.
///
/// The report that started this: `.acct-step > :last-child` in an
/// application's sheet threw from `Stylesheet.resource` in a static
/// initializer, and the application did not start.
class LenientParseTest {

    private static CssParser.Parsed lenient(String css) {
        return CssParser.parseSheet(css, ParseMode.LENIENT, "app.css");
    }

    @Nested
    @DisplayName("a lenient parse")
    class Lenient {

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "a pseudo-element              | a::before { color: red }",
                    "an attribute selector         | [aria-current] { color: red }",
                    "an attribute on a type        | a[href] { color: red }",
                    "an unknown pseudo-class       | a:hovered { color: red }",
                    "a functional pseudo-class     | lane:lang(en) { color: red }",
                    "an unsupported at-rule        | @scope (.card) { a { color: red } }",
                    "a font face                   | '@font-face { font-family: X; src: url(x.woff) }'",
                    "an import                     | @import url(other.css);",
                    "a nested pseudo-element       | a { &::before { color: red } }",
                    "a nested at-rule              | a { @scope (.b) { color: red } }",
                    "an attribute inside :is()     | :is(a[x]) { color: red }",
                })
        @DisplayName("drops only the rule asking for what the subset lacks, and keeps the rest")
        void dropsTheRuleAndKeepsTheRest(String what, String rule) {
            var parsed = lenient("one { color: red }\n" + rule + "\ntwo { color: blue }");

            assertEquals(
                    List.of("one", "two"),
                    parsed.rules().stream()
                            .map(r -> r.selectors().getFirst().key().type())
                            .toList(),
                    what);
            assertEquals(1, parsed.dropped().size(), what);
            var dropped = parsed.dropped().getFirst();
            assertEquals(2, dropped.line(), "the line the dropped rule starts on");
            assertFalse(dropped.reason().contains("(line"), "the position is the record's, not the reason's");
        }

        @Test
        @DisplayName("names the selector it dropped, as it was written")
        void namesTheSelector() {
            var dropped =
                    lenient("lane:lang(en), lane.open { color: red }").dropped().getFirst();
            assertEquals("lane:lang(en), lane.open", dropped.text());
            assertTrue(dropped.reason().contains(":lang()"), dropped.reason());
        }

        @Test
        @DisplayName("drops a rule inside @media and keeps the block's other rules under its condition")
        void insideMedia() {
            var parsed = lenient("@media (min-width: 600px) { a::after { color: red } b { color: blue } }");
            assertEquals(1, parsed.rules().size());
            assertTrue(parsed.rules().getFirst().isConditional());
            assertEquals(1, parsed.dropped().size());
        }

        @Test
        @DisplayName("and the report's own selector is in the subset now, so nothing is dropped")
        void theReportedSelectorParses() {
            var parsed = lenient(".acct-step > :last-child { margin-bottom: 0 }");
            assertEquals(1, parsed.rules().size());
            assertTrue(parsed.dropped().isEmpty());
        }

        @Test
        @DisplayName("drops a nested rule outside the subset inside @media and keeps the block's other rules")
        void nestedInsideMedia() {
            var parsed = lenient("@media (min-width: 600px) { a { &::before { color: red } } c { color: blue } }");
            assertEquals(
                    List.of("c"),
                    parsed.rules().stream()
                            .map(r -> r.selectors().getFirst().key().type())
                            .toList());
            assertEquals("&::before", parsed.dropped().getFirst().text());
            assertTrue(
                    parsed.dropped().getFirst().reason().contains("pseudo-elements"),
                    parsed.dropped().getFirst().reason());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "a { background: url(\"x{y}.png\"); color: red }",
                    "a { content: \"{\"; color: red }",
                    "a:nth-child(2n+1) { color: red }"
                })
        @DisplayName("does not mistake a brace in a string or a url for a nested rule")
        void bracesThatAreNotRules(String css) {
            var parsed = lenient(css);
            assertTrue(parsed.dropped().isEmpty(), () -> parsed.dropped().toString());
            assertEquals(1, parsed.rules().size());
        }

        @ParameterizedTest
        @ValueSource(strings = {"a { color: red", "a { color: }", "#123456 { color: red }", "a { color: red } }"})
        @DisplayName("still refuses a sheet that is malformed rather than ambitious")
        void malformedIsStillRefused(String css) {
            var thrown = assertThrows(CssSyntaxException.class, () -> lenient(css));
            assertFalse(thrown.isUnsupportedFeature());
        }
    }

    @Nested
    @DisplayName("a strict parse")
    class Strict {

        @Test
        @DisplayName("refuses the whole sheet, and says the construct is outside the subset")
        void refusesTheSheet() {
            var thrown = assertThrows(
                    CssSyntaxException.class,
                    () -> CssParser.parseSheet("a::before { color: red }", ParseMode.STRICT, "controls.css"));
            assertTrue(thrown.isUnsupportedFeature());
        }

        @Test
        @DisplayName("refuses a nested rule outside the subset, and says which construct")
        void refusesNestedByName() {
            var thrown = assertThrows(
                    CssSyntaxException.class,
                    () -> CssParser.parseSheet("a { &[x] { color: red } }", ParseMode.STRICT, "controls.css"));
            assertTrue(thrown.isUnsupportedFeature());
            assertTrue(thrown.getMessage().contains("attribute selectors"), thrown.getMessage());
        }

        @Test
        @DisplayName("is what the parser does when nobody says, which is what its tests rely on")
        void isTheParsersDefault() {
            assertThrows(CssSyntaxException.class, () -> CssParser.parse("a:hovered { color: red }"));
        }
    }

    @Nested
    @DisplayName("which sheets are which")
    class Layers {

        @Test
        @DisplayName("the toolkit base layer is strict and every other layer lenient, unless asked")
        void byLayer() {
            assertEquals(ParseMode.STRICT, Stylesheet.defaultMode(CascadeLayer.TOOLKIT_BASE));
            assertEquals(ParseMode.LENIENT, Stylesheet.defaultMode(CascadeLayer.THEME));
            assertEquals(ParseMode.LENIENT, Stylesheet.defaultMode(CascadeLayer.APPLICATION));

            var application = Stylesheet.parse(CascadeLayer.APPLICATION, "a::before { color: red } b { color: blue }");
            assertEquals(1, application.rules().size());
            assertEquals(1, application.dropped().size());

            assertThrows(
                    CssSyntaxException.class,
                    () -> Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, "a::before { color: red }"));
            assertThrows(
                    CssSyntaxException.class,
                    () -> Stylesheet.parse(CascadeLayer.APPLICATION, "a::before { color: red }", ParseMode.STRICT));
        }

        @Test
        @DisplayName("a sheet is named in what it warns about")
        void origin() {
            assertEquals(
                    "app.css",
                    Stylesheet.parse(CascadeLayer.APPLICATION, "", ParseMode.LENIENT, "app.css")
                            .origin());
            assertEquals(
                    "a stylesheet",
                    Stylesheet.parse(CascadeLayer.APPLICATION, "").origin());
        }
    }
}
