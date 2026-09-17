package io.github.digitalsmile.goldberry.css.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.css.Keyframes;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;

/// The two at-rules motion needs: `@starting-style` ([ADR-0352]) and `@keyframes`
/// ([ADR-0353]).
///
/// The parser's job is the same for both as for every rule: keep what is in the
/// subset and refuse, with a position, what is not.
class MotionAtRulesTest {

    @Nested
    @DisplayName("@starting-style")
    class StartingStyle {

        @Test
        @DisplayName("its rules are ordinary rules, marked, and in their source position")
        void marksItsRules() {
            var rules = CssParser.parse("""
                    button.float { opacity: 1 }
                    @starting-style {
                      button.float { opacity: 0 }
                      toast { transform: translateY(8px) }
                    }
                    button { color: red }
                    """);

            assertEquals(4, rules.size());
            assertEquals(
                    List.of(false, true, true, false),
                    rules.stream().map(r -> r.starting()).toList());
            assertEquals(List.of(0, 1, 2, 3), rules.stream().map(r -> r.order()).toList());
        }

        @Test
        @DisplayName("a rule outside it is not a starting rule, whatever it says")
        void plainRulesAreNot() {
            assertFalse(CssParser.parse("a { opacity: 0 }").getFirst().starting());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "@starting-style (x) { a { opacity: 0 } }",
                    "@starting-style { a { opacity: 0 }",
                    "@starting-style { @keyframes k { from { opacity: 0 } } }",
                })
        @DisplayName("a prelude, an unclosed block or a nested at-rule is refused")
        void refusesWhatIsNotInTheSubset(String css) {
            assertThrows(CssSyntaxException.class, () -> CssParser.parse(css));
        }
    }

    @Nested
    @DisplayName("@keyframes")
    class KeyframesRule {

        @Test
        @DisplayName("from, to and percentages become sorted frames, one per offset")
        void framesAreSortedAndSplit() {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, """
                    @keyframes tile-drop {
                      to { transform: none }
                      from, 40% { opacity: 0 }
                      60% { opacity: 1 }
                    }
                    a { animation: tile-drop 850ms }
                    """);

            assertEquals(1, sheet.rules().size(), "a keyframes block is not a style rule");
            var block = sheet.keyframes().getFirst();
            assertEquals("tile-drop", block.name());
            assertEquals(
                    List.of(0.0, 0.4, 0.6, 1.0),
                    block.frames().stream().map(Keyframes.Frame::offset).toList());
            assertEquals(
                    "opacity", block.frames().get(1).declarations().getFirst().property());
        }

        @Test
        @DisplayName("two blocks with one name are both kept; the cascade picks the later")
        void bothKept() {
            var parsed =
                    CssParser.parseSheet("@keyframes k { from { opacity: 0 } } @keyframes k { to { opacity: 0 } }");

            assertEquals(2, parsed.keyframes().size());
        }

        @Test
        @DisplayName("parse(String) still answers rules alone, for every caller written before keyframes")
        void rulesOnly() {
            assertTrue(CssParser.parse("@keyframes k { from { opacity: 0 } }").isEmpty());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "@keyframes { from { opacity: 0 } }",
                    "@keyframes none { from { opacity: 0 } }",
                    "@keyframes k { 120% { opacity: 0 } }",
                    "@keyframes k { middle { opacity: 0 } }",
                    "@keyframes k { from { opacity: 0 !important } }",
                    "@keyframes k { from { opacity: 0 }",
                })
        @DisplayName("no name, `none`, an offset out of range, a word, !important or an unclosed block is refused")
        void refusesWhatIsNotInTheSubset(String css) {
            assertThrows(CssSyntaxException.class, () -> CssParser.parse(css));
        }
    }
}
