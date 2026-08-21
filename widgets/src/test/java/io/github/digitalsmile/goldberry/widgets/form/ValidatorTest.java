package io.github.digitalsmile.goldberry.widgets.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// §4's validation model, with no widget, no font and no window.
///
/// The rules a `field` applies are all in here, which is the point of a validator
/// being a value: the field decides *when* to ask and this decides *what the
/// answer is*.
class ValidatorTest {

    @Nested
    @DisplayName("the result")
    class Results {

        @Test
        @DisplayName("carries a message rather than a boolean")
        void carriesTheReason() {
            var result = Validator.Result.invalid("That is not a port");

            assertFalse(result.isValid());
            assertEquals("That is not a port", result.message());
            assertTrue(Validator.Result.VALID.isValid());
        }

        @Test
        @DisplayName("refuses a failure with nothing to say")
        void refusesABlankMessage() {
            // A field that goes red and does not say why is a field somebody has
            // to guess at, which is the whole reason the result is not a boolean.
            assertThrows(IllegalArgumentException.class, () -> Validator.Result.invalid(""));
            assertThrows(IllegalArgumentException.class, () -> Validator.Result.invalid("   "));
        }
    }

    @Nested
    @DisplayName("the rules that ship")
    class Builtin {

        @Test
        @DisplayName("required refuses null, empty and whitespace")
        void required() {
            var rule = Validator.required("Needed");

            assertFalse(rule.check(null).isValid());
            assertFalse(rule.check("").isValid());
            // Three spaces is not a name. A form that accepted it is one somebody
            // discovers the hard way.
            assertFalse(rule.check("   ").isValid());
            assertTrue(rule.check("Jane").isValid());
        }

        @Test
        @DisplayName("minLength counts characters")
        void minLength() {
            var rule = Validator.minLength(3, "Too short");

            assertFalse(rule.check(null).isValid());
            assertFalse(rule.check("ab").isValid());
            assertTrue(rule.check("abc").isValid());
        }

        @Test
        @DisplayName("a pattern lets an empty value through")
        void patternAllowsEmpty() {
            var rule = Validator.matching(Pattern.compile("\\d+"), "Digits only");

            // "This must look like a number" and "this must be filled in" are two
            // rules. A pattern that also refused emptiness would turn every
            // optional field with a format into a required one.
            assertTrue(rule.check("").isValid());
            assertTrue(rule.check(null).isValid());
            assertTrue(rule.check("42").isValid());
            assertFalse(rule.check("4x").isValid());
        }

        @Test
        @DisplayName("a pattern has to match the whole value")
        void patternMatchesInFull() {
            var rule = Validator.matching(Pattern.compile("\\d+"), "Digits only");

            // `matches` and not `find`: a rule that accepted "12abc" because it
            // contains digits is a rule that accepts anything.
            assertFalse(rule.check("12abc").isValid());
        }

        @Test
        @DisplayName("none accepts everything, including nothing")
        void none() {
            assertTrue(Validator.none().check(null).isValid());
            assertTrue(Validator.none().check("anything").isValid());
        }
    }

    @Nested
    @DisplayName("composing")
    class Composing {

        @Test
        @DisplayName("reports the first failure, not all of them")
        void firstFailureWins() {
            var rule = Validator.required("Needed")
                    .and(Validator.minLength(3, "Too short"));

            // A field's message slot is one line, and a list of three complaints
            // about one value is a worse message than the first one.
            assertEquals("Needed", rule.check("").message());
            assertEquals("Too short", rule.check("ab").message());
            assertTrue(rule.check("abc").isValid());
        }

        @Test
        @DisplayName("the second rule is not run when the first fails")
        void shortCircuits() {
            var ran = new boolean[1];
            var rule = Validator.required("Needed").and(value -> {
                ran[0] = true;
                return Validator.Result.VALID;
            });

            rule.check("");

            // Which matters as soon as a rule is expensive — a format check with
            // a big pattern, or one that parses a date.
            assertFalse(ran[0]);
        }

        @Test
        @DisplayName("a predicate and a message is the form most rules want")
        void fromAPredicate() {
            var rule = Validator.of(value -> "8080".equals(value), "Wrong port");

            assertTrue(rule.check("8080").isValid());
            assertEquals("Wrong port", rule.check("80").message());
        }
    }
}
