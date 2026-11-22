package dev.goldberry.css.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.text.flow.DecorationPaint;
import dev.goldberry.text.flow.DecorationStyle;
import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.text.flow.TextDecorations;

/// `text-decoration` and its longhands as values, apart from the cascade.
class TextDecorationParserTest {

    /// `em` is 20 pixels here, so a percentage is a fifth of a pixel per cent.
    private static final CssLength.Context CONTEXT = new CssLength.Context(20, 16);

    @Nested
    @DisplayName("the shorthand")
    class Shorthand {

        @Test
        @DisplayName("reads its four parts in any order")
        void anyOrder() {
            var expected = new TextDecorations(
                    Set.of(TextDecoration.UNDERLINE),
                    new DecorationPaint(DecorationStyle.WAVY, OptionalInt.of(0xFFFF0000), 2, Double.NaN));

            assertEquals(Optional.of(expected), shorthand("underline wavy red 2px"));
            assertEquals(Optional.of(expected), shorthand("2px red wavy underline"));
            assertEquals(Optional.of(expected), shorthand("wavy 2px underline red"));
        }

        @Test
        @DisplayName("takes both lines as one part, and a colour function as one")
        void groupedParts() {
            var read = shorthand("underline line-through rgb(255 0 0) dotted").orElseThrow();

            assertEquals(Set.of(TextDecoration.UNDERLINE, TextDecoration.LINE_THROUGH), read.lines());
            assertEquals(OptionalInt.of(0xFFFF0000), read.paint().colour());
            assertEquals(DecorationStyle.DOTTED, read.paint().style());
        }

        @Test
        @DisplayName("with no line at all is a pattern for lines a longhand may name later")
        void styleAlone() {
            var read = shorthand("wavy").orElseThrow();

            assertEquals(TextDecoration.NONE, read.lines());
            assertEquals(DecorationStyle.WAVY, read.paint().style());
        }

        @Test
        @DisplayName("keeps the underline offset it was given")
        void keepsOffset() {
            var current = TextDecorations.NONE.paint(DecorationPaint.SOLID.underlineOffset(4));

            var read = TextDecorationParser.shorthand(tokens("underline"), CONTEXT, current)
                    .orElseThrow();

            assertEquals(4.0, read.paint().underlineOffset());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "underline wavy dotted",
                    "underline red blue",
                    "underline 1px 2px",
                    "underline overline",
                    "underline blink",
                    "none underline",
                    "underline zigzag",
                    "underline -1px",
                    ""
                })
        @DisplayName("refuses a part twice, an unknown part, and a contradiction")
        void refusals(String value) {
            assertEquals(Optional.empty(), shorthand(value), value);
        }
    }

    @Nested
    @DisplayName("the longhands")
    class Longhands {

        @Test
        @DisplayName("lines: none, one or both")
        void lines() {
            assertEquals(Optional.of(TextDecoration.NONE), TextDecorationParser.lines(tokens("none")));
            assertEquals(
                    Optional.of(Set.of(TextDecoration.LINE_THROUGH)),
                    TextDecorationParser.lines(tokens("line-through")));
            assertEquals(Optional.empty(), TextDecorationParser.lines(tokens("underline underline")));
        }

        @Test
        @DisplayName("style: the five keywords, in any case")
        void style() {
            for (var style : DecorationStyle.values()) {
                assertEquals(
                        Optional.of(style),
                        TextDecorationParser.style(tokens(style.cssName().toUpperCase(Locale.ROOT))));
            }
            assertEquals(Optional.empty(), TextDecorationParser.style(tokens("wavy dotted")));
        }

        @Test
        @DisplayName("thickness: auto and from-font are the face's, a percentage is of the em")
        void thickness() {
            assertTrue(TextDecorationParser.thickness(tokens("auto"), CONTEXT)
                    .orElseThrow()
                    .isNaN());
            assertTrue(TextDecorationParser.thickness(tokens("from-font"), CONTEXT)
                    .orElseThrow()
                    .isNaN());
            assertEquals(Optional.of(3.0), TextDecorationParser.thickness(tokens("3px"), CONTEXT));
            assertEquals(Optional.of(2.0), TextDecorationParser.thickness(tokens("10%"), CONTEXT));
            assertEquals(Optional.of(10.0), TextDecorationParser.thickness(tokens("0.5em"), CONTEXT));
            assertEquals(Optional.of(0.0), TextDecorationParser.thickness(tokens("0"), CONTEXT));
            assertEquals(Optional.empty(), TextDecorationParser.thickness(tokens("-1px"), CONTEXT));
        }

        @Test
        @DisplayName("offset: auto is the face's, and it may be negative")
        void offset() {
            assertTrue(TextDecorationParser.offset(tokens("auto"), CONTEXT)
                    .orElseThrow()
                    .isNaN());
            assertEquals(Optional.of(-2.0), TextDecorationParser.offset(tokens("-2px"), CONTEXT));
            assertEquals(Optional.of(22.0), TextDecorationParser.offset(tokens("calc(2px + 1em)"), CONTEXT));
            assertEquals(Optional.of(2.0), TextDecorationParser.offset(tokens("10%"), CONTEXT));
            assertEquals(Optional.empty(), TextDecorationParser.offset(tokens("red"), CONTEXT));
        }

        @Test
        @DisplayName("colour: any colour, and nothing else")
        void colour() {
            assertEquals(Optional.of(0xFF00FF00), TextDecorationParser.colour(tokens("#0f0")));
            assertEquals(Optional.empty(), TextDecorationParser.colour(tokens("2px")));
        }
    }

    private static Optional<TextDecorations> shorthand(String value) {
        return TextDecorationParser.shorthand(tokens(value), CONTEXT, TextDecorations.NONE);
    }

    private static List<Token> tokens(String value) {
        return CssTokenizer.tokenize(value).stream()
                .filter(t -> !t.is(TokenType.EOF))
                .toList();
    }
}
