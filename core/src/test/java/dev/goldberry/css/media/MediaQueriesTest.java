package dev.goldberry.css.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.render.desktop.SystemTheme;

/// Reading an `@media` prelude, and answering it against a window.
class MediaQueriesTest {

    /// A prelude as the stylesheet parser hands it over: the tokens of the text,
    /// without the end-of-input token.
    private static MediaCondition parse(String prelude) {
        var tokens = new ArrayList<Token>();
        for (var token : CssTokenizer.tokenize(prelude)) {
            if (!token.is(TokenType.EOF)) {
                tokens.add(token);
            }
        }
        return MediaQueries.parse(tokens);
    }

    private static final MediaContext WIDE = MediaContext.UNKNOWN.size(1280, 800);
    private static final MediaContext NARROW = MediaContext.UNKNOWN.size(480, 800);

    @Nested
    @DisplayName("sizes")
    class Sizes {

        @ParameterizedTest(name = "{0} at 1280x800 is {1}, at 480x800 is {2}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "(min-width: 600px)             | true  | false",
                    "(max-width: 600px)             | false | true",
                    "(width >= 600px)               | true  | false",
                    "(width < 600px)                | false | true",
                    "(min-height: 700px)            | true  | true",
                    "(max-height: 700px)            | false | false",
                    "(min-width: 40em)              | true  | false",
                    "(orientation: landscape)       | true  | false",
                    "(orientation: portrait)        | false | true",
                    "screen and (min-width: 600px)  | true  | false",
                    "only screen and (max-width: 600px) | false | true",
                    "not (min-width: 600px)         | false | true",
                    "(min-width: 600px) and (max-width: 1000px) | false | false",
                    "(max-width: 500px) or (min-width: 1000px)  | true  | true",
                    "(max-width: 500px), (min-width: 1000px)    | true  | true",
                    "print                          | false | false",
                    "not print                      | true  | true",
                    "all                            | true  | true",
                })
        @DisplayName("are answered against the window's logical size")
        void answered(String prelude, boolean wide, boolean narrow) {
            var condition = parse(prelude);
            assertTrue(MediaCondition.unsupported(condition).isEmpty(), () -> condition.toString());
            assertEquals(wide, condition.matches(WIDE), "wide");
            assertEquals(narrow, condition.matches(NARROW), "narrow");
        }

        @Test
        @DisplayName("hold nowhere while the window has not said its size")
        void unknownSize() {
            assertFalse(parse("(min-width: 0px)").matches(MediaContext.UNKNOWN));
            assertFalse(parse("(max-width: 100000px)").matches(MediaContext.UNKNOWN));
        }

        @Test
        @DisplayName("an empty prelude is every window")
        void empty() {
            assertSame(MediaCondition.ALWAYS, MediaQueries.parse(java.util.List.of()));
        }
    }

    @Nested
    @DisplayName("preferences")
    class Preferences {

        @Test
        @DisplayName("prefers-color-scheme follows the desktop's theme")
        void colorScheme() {
            var dark = parse("(prefers-color-scheme: dark)");
            assertFalse(dark.matches(WIDE), "a desktop that does not say is light");
            assertTrue(dark.matches(WIDE.colorScheme(SystemTheme.DARK)));
            assertTrue(parse("(prefers-color-scheme: light)").matches(WIDE));
        }

        @Test
        @DisplayName("prefers-reduced-motion follows the renderer's switch")
        void reducedMotion() {
            var reduce = parse("(prefers-reduced-motion: reduce)");
            assertFalse(reduce.matches(WIDE));
            assertTrue(reduce.matches(WIDE.reducedMotion(true)));
            assertTrue(parse("(prefers-reduced-motion: no-preference)").matches(WIDE));
        }
    }

    @Nested
    @DisplayName("what it cannot answer")
    class Unsupported {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "(hover: hover)",
                    "(min-resolution: 2dppx)",
                    "(min-width: 30vw)",
                    "tv-ish and (min-width: 1px)",
                    "(min-width 600px)",
                    "(min-width: 600px) and (max-width: 900px) or (hover: none)",
                })
        @DisplayName("never holds, with a reason, and is never rescued by not")
        void neverHolds(String prelude) {
            var condition = parse(prelude);
            assertInstanceOf(MediaCondition.Unsupported.class, condition);
            assertTrue(MediaCondition.unsupported(condition).isPresent());
            assertFalse(condition.matches(WIDE));
            assertFalse(new MediaCondition.Not(condition).matches(WIDE));
        }

        @Test
        @DisplayName("one unreadable query in a list leaves the others counting, as CSS says")
        void listKeepsTheRest() {
            var condition = parse("(hover: hover), (min-width: 600px)");
            assertTrue(condition.matches(WIDE));
            assertFalse(condition.matches(NARROW));
            assertTrue(MediaCondition.unsupported(condition).isPresent(), "and it is still reported");
        }
    }
}
